package com.mantelmakher.crm.data.repository

import com.mantelmakher.crm.data.local.ActivityDao
import com.mantelmakher.crm.data.local.ClientDao
import com.mantelmakher.crm.data.local.DealDao
import com.mantelmakher.crm.data.local.PhotoDao
import com.mantelmakher.crm.data.local.PropertyDao
import com.mantelmakher.crm.data.local.TaskDao
import com.mantelmakher.crm.data.photos.PhotoStore
import com.mantelmakher.crm.domain.logic.Timestamps
import com.mantelmakher.crm.domain.logic.matchesFilter
import com.mantelmakher.crm.domain.logic.matchesQuery
import com.mantelmakher.crm.domain.model.ActivityEntry
import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.ClientKind
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.model.Deal
import com.mantelmakher.crm.domain.model.DealListItem
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.ListingPurpose
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.model.PropertyPhoto
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.domain.model.PropertyType
import com.mantelmakher.crm.domain.model.TaskFilter
import com.mantelmakher.crm.domain.repository.ActivityRepository
import com.mantelmakher.crm.domain.repository.ClientRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.PropertyRepository
import com.mantelmakher.crm.domain.repository.ReminderScheduler
import com.mantelmakher.crm.domain.repository.TaskRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomClientRepository(
    private val dao: ClientDao,
) : ClientRepository {
    override fun observe(query: String, kind: ClientKind?): Flow<List<Client>> =
        dao.observeActive().map { rows ->
            rows.map { it.toModel() }
                .filter { kind == null || it.kind == kind }
                .filter { it.matchesQuery(query) }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeById(id: String): Flow<Client?> = dao.observeById(id).map { it?.toModel() }

    override suspend fun get(id: String): Client? = dao.get(id)?.toModel()

    override suspend fun save(client: Client) {
        val existing = dao.get(client.id)
        val now = System.currentTimeMillis()
        val (created, updated) = Timestamps.stamp(existing?.createdAt, client.createdAt, now)
        dao.upsert(client.copy(createdAt = created, updatedAt = updated, deletedAt = null).toEntity())
    }

    override suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now, now)
    }
}

class RoomPropertyRepository(
    private val properties: PropertyDao,
    private val photos: PhotoDao,
    private val photoStore: PhotoStore,
) : PropertyRepository {
    override fun observe(
        query: String,
        purpose: ListingPurpose?,
        type: PropertyType?,
        status: PropertyStatus?,
    ): Flow<List<Property>> = properties.observeActive().map { rows ->
        rows.map { it.toModel() }
            .filter { purpose == null || it.purpose == purpose }
            .filter { type == null || it.type == type }
            .filter { status == null || it.status == status }
            .filter { it.matchesQuery(query) }
            .sortedByDescending { it.updatedAt }
    }

    override fun observeById(id: String): Flow<Property?> = properties.observeById(id).map { it?.toModel() }

    override suspend fun get(id: String): Property? = properties.get(id)?.toModel()

    override fun observePhotos(propertyId: String): Flow<List<PropertyPhoto>> =
        photos.observeForProperty(propertyId).map { rows -> rows.map { it.toModel() } }

    override suspend fun save(property: Property) {
        val existing = properties.get(property.id)
        val now = System.currentTimeMillis()
        val (created, updated) = Timestamps.stamp(existing?.createdAt, property.createdAt, now)
        properties.upsert(property.copy(createdAt = created, updatedAt = updated, deletedAt = null).toEntity())
    }

    override suspend fun savePhoto(photo: PropertyPhoto) {
        val existing = photos.get(photo.id)
        val now = System.currentTimeMillis()
        val (created, updated) = Timestamps.stamp(existing?.createdAt, photo.createdAt, now)
        photos.upsert(photo.copy(createdAt = created, updatedAt = updated, deletedAt = null).toEntity())
    }

    override suspend fun nextPhotoSort(propertyId: String): Int = photos.maxSort(propertyId) + 1

    override suspend fun softDeletePhoto(id: String, fileName: String) {
        val now = System.currentTimeMillis()
        photos.softDelete(id, now, now)
        photoStore.delete(fileName)
    }

    override suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        properties.softDelete(id, now, now)
    }

    override fun absolutePhotoPath(fileName: String): String = photoStore.absolutePath(fileName)
}

class RoomDealRepository(
    private val deals: DealDao,
    private val clients: ClientDao,
    private val properties: PropertyDao,
) : DealRepository {
    private fun enriched(): Flow<List<DealListItem>> = combine(
        deals.observeActive(),
        clients.observeActive(),
        properties.observeActive(),
    ) { dealRows, clientRows, propertyRows ->
        val names = clientRows.associate { it.id to it.fullName }
        val titles = propertyRows.associate { it.id to it.title }
        dealRows.map { row ->
            val deal = row.toModel()
            DealListItem(deal, names[deal.clientId], titles[deal.propertyId])
        }
    }

    override fun observe(query: String, stage: DealStage?): Flow<List<DealListItem>> =
        enriched().map { rows ->
            rows.filter { stage == null || it.deal.stage == stage }
                .filter { it.deal.matchesQuery(query, it.clientName, it.propertyTitle) }
                .sortedByDescending { it.deal.updatedAt }
        }

    override fun observeById(id: String): Flow<Deal?> = deals.observeById(id).map { it?.toModel() }

    override fun observeForClient(clientId: String): Flow<List<DealListItem>> =
        observe("", null).map { rows -> rows.filter { it.deal.clientId == clientId } }

    override fun observeForProperty(propertyId: String): Flow<List<DealListItem>> =
        observe("", null).map { rows -> rows.filter { it.deal.propertyId == propertyId } }

    override suspend fun get(id: String): Deal? = deals.get(id)?.toModel()

    override suspend fun save(deal: Deal) {
        val existing = deals.get(deal.id)
        val now = System.currentTimeMillis()
        val (created, updated) = Timestamps.stamp(existing?.createdAt, deal.createdAt, now)
        deals.upsert(deal.copy(createdAt = created, updatedAt = updated, deletedAt = null).toEntity())
    }

    override suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        deals.softDelete(id, now, now)
    }
}

class RoomTaskRepository(
    private val dao: TaskDao,
    private val reminders: ReminderScheduler,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TaskRepository {
    override fun observe(filter: TaskFilter, query: String): Flow<List<CrmTask>> =
        dao.observeActive().map { rows ->
            val now = clock()
            rows.map { it.toModel() }
                .filter { it.matchesFilter(filter, now, zone) }
                .filter { it.matchesQuery(query) }
                .sortedWith(taskSort(filter, now))
        }

    override fun observeById(id: String): Flow<CrmTask?> = dao.observeById(id).map { it?.toModel() }

    override fun observeForClient(clientId: String): Flow<List<CrmTask>> =
        observe(TaskFilter.ALL, "").map { rows -> rows.filter { it.clientId == clientId } }

    override fun observeForDeal(dealId: String): Flow<List<CrmTask>> =
        observe(TaskFilter.ALL, "").map { rows -> rows.filter { it.dealId == dealId } }

    override fun observeForProperty(propertyId: String): Flow<List<CrmTask>> =
        observe(TaskFilter.ALL, "").map { rows -> rows.filter { it.propertyId == propertyId } }

    override suspend fun get(id: String): CrmTask? = dao.get(id)?.toModel()

    override suspend fun save(task: CrmTask) {
        val existing = dao.get(task.id)
        val now = System.currentTimeMillis()
        val (created, updated) = Timestamps.stamp(existing?.createdAt, task.createdAt, now)
        val stored = task.copy(createdAt = created, updatedAt = updated, deletedAt = null)
        dao.upsert(stored.toEntity())
        reminders.schedule(stored)
    }

    override suspend fun setCompleted(id: String, completed: Boolean) {
        val current = get(id) ?: return
        val now = System.currentTimeMillis()
        save(current.copy(completedAt = if (completed) now else null, updatedAt = now))
    }

    override suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now, now)
        reminders.cancel(id)
    }

    override suspend fun rescheduleAll() {
        dao.reminderCandidates().forEach { reminders.schedule(it.toModel()) }
    }

    private fun taskSort(filter: TaskFilter, now: Long): Comparator<CrmTask> {
        if (filter == TaskFilter.DONE) {
            return compareByDescending<CrmTask> { it.completedAt ?: 0L }
        }
        return compareBy<CrmTask> { task ->
            if (com.mantelmakher.crm.domain.logic.DayWindow.isOverdue(task.dueAt, task.completedAt, now, zone)) 0 else 1
        }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
            .thenByDescending { it.updatedAt }
    }
}

class RoomActivityRepository(
    private val dao: ActivityDao,
) : ActivityRepository {
    override fun observeForClient(clientId: String): Flow<List<ActivityEntry>> =
        dao.observeForClient(clientId).map { rows -> rows.map { it.toModel() } }

    override fun observeForDeal(dealId: String): Flow<List<ActivityEntry>> =
        dao.observeForDeal(dealId).map { rows -> rows.map { it.toModel() } }

    override fun observeForProperty(propertyId: String): Flow<List<ActivityEntry>> =
        dao.observeForProperty(propertyId).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(entry: ActivityEntry) {
        val now = System.currentTimeMillis()
        val (created, updated) = Timestamps.stamp(null, entry.createdAt, now)
        dao.upsert(entry.copy(createdAt = created, updatedAt = updated, deletedAt = null).toEntity())
    }

    override suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now, now)
    }
}
