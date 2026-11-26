package com.mantelmakher.crm.domain.repository

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
import kotlinx.coroutines.flow.Flow

/**
 * Persistence ports. The Room implementation is local-only.
 * A later LAN server or hosted backend should implement these same interfaces
 * and can honor [Client.deletedAt] (and the same flag on every record) as a tombstone.
 */
interface ClientRepository {
    fun observe(query: String, kind: ClientKind?): Flow<List<Client>>
    fun observeById(id: String): Flow<Client?>
    suspend fun get(id: String): Client?
    suspend fun save(client: Client)
    suspend fun softDelete(id: String)
}

interface PropertyRepository {
    fun observe(
        query: String,
        purpose: ListingPurpose?,
        type: PropertyType?,
        status: PropertyStatus?,
    ): Flow<List<Property>>

    fun observeById(id: String): Flow<Property?>
    suspend fun get(id: String): Property?
    fun observePhotos(propertyId: String): Flow<List<PropertyPhoto>>
    suspend fun save(property: Property)
    suspend fun savePhoto(photo: PropertyPhoto)
    suspend fun nextPhotoSort(propertyId: String): Int
    suspend fun softDeletePhoto(id: String, fileName: String)
    suspend fun softDelete(id: String)
    fun absolutePhotoPath(fileName: String): String
}

interface DealRepository {
    fun observe(query: String, stage: DealStage?): Flow<List<DealListItem>>
    fun observeById(id: String): Flow<Deal?>
    fun observeForClient(clientId: String): Flow<List<DealListItem>>
    fun observeForProperty(propertyId: String): Flow<List<DealListItem>>
    suspend fun get(id: String): Deal?
    suspend fun save(deal: Deal)
    suspend fun softDelete(id: String)
}

interface TaskRepository {
    fun observe(filter: TaskFilter, query: String): Flow<List<CrmTask>>
    fun observeById(id: String): Flow<CrmTask?>
    fun observeForClient(clientId: String): Flow<List<CrmTask>>
    fun observeForDeal(dealId: String): Flow<List<CrmTask>>
    fun observeForProperty(propertyId: String): Flow<List<CrmTask>>
    suspend fun get(id: String): CrmTask?
    suspend fun save(task: CrmTask)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun softDelete(id: String)
    suspend fun rescheduleAll()
}

interface ActivityRepository {
    fun observeForClient(clientId: String): Flow<List<ActivityEntry>>
    fun observeForDeal(dealId: String): Flow<List<ActivityEntry>>
    fun observeForProperty(propertyId: String): Flow<List<ActivityEntry>>
    suspend fun add(entry: ActivityEntry)
    suspend fun softDelete(id: String)
}

interface ReminderScheduler {
    fun schedule(task: CrmTask)
    fun cancel(taskId: String)
}

interface UserSettings {
    val languageTag: Flow<String>
    val currencyCode: Flow<String>
    suspend fun setLanguage(tag: String)
    suspend fun setCurrency(code: String)
}
