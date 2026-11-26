package com.intelliverse.croman.data.repo

import androidx.room.withTransaction
import com.intelliverse.croman.data.local.ActivityEntity
import com.intelliverse.croman.data.local.CalendarEntity
import com.intelliverse.croman.data.local.CatalogEntity
import com.intelliverse.croman.data.local.ChangeLogEntity
import com.intelliverse.croman.data.local.ContactEntity
import com.intelliverse.croman.data.local.CromanDatabase
import com.intelliverse.croman.data.local.DocumentEntity
import com.intelliverse.croman.data.local.EntityRecord
import com.intelliverse.croman.data.local.EntityTypeEntity
import com.intelliverse.croman.data.local.FieldDefinitionEntity
import com.intelliverse.croman.data.local.FieldValueEntity
import com.intelliverse.croman.data.local.MessageEntity
import com.intelliverse.croman.data.local.PaymentEntity
import com.intelliverse.croman.data.local.PipelineEntity
import com.intelliverse.croman.data.local.RelationEntity
import com.intelliverse.croman.data.local.StageEntity
import com.intelliverse.croman.data.local.SyncStamp
import com.intelliverse.croman.data.local.TaskEntity
import com.intelliverse.croman.data.local.ViewConfigEntity
import com.intelliverse.croman.data.local.WorkspaceEntity
import com.intelliverse.croman.data.photos.PhotoStore
import com.intelliverse.croman.domain.ActivityEntry
import com.intelliverse.croman.domain.CalendarEvent
import com.intelliverse.croman.domain.CatalogItem
import com.intelliverse.croman.domain.Change
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.ContactKind
import com.intelliverse.croman.domain.DocumentFile
import com.intelliverse.croman.domain.EntityRow
import com.intelliverse.croman.domain.EntityTypeDef
import com.intelliverse.croman.domain.FieldType
import com.intelliverse.croman.domain.LessonSeries
import com.intelliverse.croman.domain.AppointmentEngine
import com.intelliverse.croman.domain.CategoryCatalog
import com.intelliverse.croman.domain.PackageCharge
import com.intelliverse.croman.domain.HomeCalculator
import com.intelliverse.croman.domain.HomeSummary
import com.intelliverse.croman.domain.Ids
import com.intelliverse.croman.domain.LocalUser
import com.intelliverse.croman.domain.MessageEntry
import com.intelliverse.croman.domain.MessageStatus
import com.intelliverse.croman.domain.PaymentKind
import com.intelliverse.croman.domain.PaymentRecord
import com.intelliverse.croman.domain.TaskFilter
import com.intelliverse.croman.domain.TaskItem
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.domain.TemplateMigrations
import com.intelliverse.croman.domain.TextFold
import com.intelliverse.croman.domain.Workspace
import com.intelliverse.croman.domain.DayWindow
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CromanStore(
    private val database: () -> CromanDatabase,
    private val userId: String = LocalUser.ID,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Test hook. When set to a point name, the matching write throws and the transaction rolls back. */
    var faultAt: String? = null

    private fun fault(point: String) {
        if (faultAt == point) throw IllegalStateException("injected failure at $point")
    }
    private val json = Json { ignoreUnknownKeys = true }

    fun observeWorkspaces(): Flow<List<Workspace>> =
        database().workspaces().observe().map { rows -> rows.map { it.toModel() } }

    suspend fun installWorkspace(template: TemplateFile, name: String): String {
        val now = clock()
        val id = Ids.new()
        val db = database()
        db.withTransaction {
        db.workspaces().upsert(
            WorkspaceEntity(
                id = id,
                sync = stamp(id, now, now),
                name = name,
                templateId = template.id,
                templateVersion = template.version,
                country = template.country,
                category = template.category.ifBlank { CategoryCatalog.byTemplate(template.id)?.id.orEmpty() },
                primaryProcess = template.process.ifBlank { CategoryCatalog.byTemplate(template.id)?.process?.wire.orEmpty() },
                enabledProcesses = template.process.ifBlank { CategoryCatalog.byTemplate(template.id)?.process?.wire.orEmpty() },
            ),
        )
        fault("workspace-row")
        db.config().upsertView(
            ViewConfigEntity(
                id = Ids.new(),
                sync = stamp(id, now, now),
                configKey = "template",
                bodyJson = json.encodeToString(template),
            ),
        )
        template.entityTypes.forEachIndexed { index, type ->
            db.config().upsertType(
                EntityTypeEntity(
                    id = Ids.new(),
                    sync = stamp(id, now, now),
                    typeKey = type.key,
                    labelEn = type.label.en,
                    labelRu = type.label.ru,
                    labelHe = type.label.he,
                    hasPhotos = type.hasPhotos,
                    pipelineKey = type.pipeline?.key,
                    sortOrder = index,
                    listBadge = type.listBadge,
                    listMoney = type.listMoney,
                    listSubtitle = type.listSubtitle.joinToString(","),
                    definitionJson = json.encodeToString(type),
                ),
            )
            type.fields.forEachIndexed { fieldIndex, field ->
                db.config().upsertField(
                    FieldDefinitionEntity(
                        id = Ids.new(),
                        sync = stamp(id, now, now),
                        entityTypeKey = type.key,
                        fieldKey = field.key,
                        labelEn = field.label.en,
                        labelRu = field.label.ru,
                        labelHe = field.label.he,
                        fieldType = field.type,
                        required = field.required,
                        sortOrder = fieldIndex,
                        optionsJson = json.encodeToString(field.options),
                        referenceType = field.referenceType,
                    ),
                )
            }
            type.pipeline?.let { pipeline ->
                db.config().upsertPipeline(
                    PipelineEntity(Ids.new(), stamp(id, now, now), pipeline.key, type.key),
                )
                pipeline.stages.forEachIndexed { stageIndex, stage ->
                    db.config().upsertStage(
                        StageEntity(
                            id = Ids.new(),
                            sync = stamp(id, now, now),
                            pipelineKey = pipeline.key,
                            stageKey = stage.key,
                            labelEn = stage.label.en,
                            labelRu = stage.label.ru,
                            labelHe = stage.label.he,
                            sortOrder = stageIndex,
                            closedWon = stage.won,
                            closedLost = stage.lost,
                        ),
                    )
                }
            }
        }
        audit(id, "workspaces", id, "upsert")
        }
        return id
    }

    suspend fun upgradeWorkspace(workspaceId: String, bundled: TemplateFile) {
        val current = database().workspaces().get(workspaceId) ?: return
        if (current.templateVersion >= bundled.version) return
        val steps = TemplateMigrations.plan(bundled.migrations, current.templateVersion, bundled.version)
        val now = clock()
        database().withTransaction {
        steps.forEach { step ->
            step.renames.forEach { rename ->
                database().config().renameField(workspaceId, rename.type, rename.from, rename.to, now)
                database().config().renameValuesForType(workspaceId, rename.type, rename.from, rename.to, now)
            }
            step.splits.forEach { split ->
                val ids = database().entities().idsOf(workspaceId, split.type)
                database().entities().valuesFor(ids).filter { it.fieldKey == split.from }.forEach { row ->
                    val migrated = TemplateMigrations.apply(
                        mapOf(split.from to row.valueText.orEmpty()),
                        listOf(step),
                        step.from,
                        step.to,
                        split.type,
                    )
                    migrated.forEach { (key, value) ->
                        if (key == split.from) return@forEach
                        database().entities().upsertValue(
                            row.copy(id = "${row.entityId}:$key", fieldKey = key, valueText = value),
                        )
                    }
                    if (migrated.keys.any { it != split.from }) {
                        database().entities().deleteValue(row.entityId, split.from)
                    }
                }
            }
        }
        database().workspaces().upsert(
            current.copy(templateVersion = bundled.version, sync = current.sync.copy(updatedAt = now, updatedByUserId = userId)),
        )
        }
        audit(workspaceId, "workspaces", workspaceId, "upsert")
    }

    suspend fun renameWorkspace(id: String, name: String) {
        val current = database().workspaces().get(id) ?: return
        val clean = name.trim()
        if (clean.isEmpty()) throw IllegalArgumentException("name")
        val now = clock()
        database().workspaces().upsert(current.copy(name = clean, sync = current.sync.copy(updatedAt = now)))
        audit(id, "workspaces", id, "upsert")
    }

    suspend fun enableProcess(id: String, process: String) {
        val current = database().workspaces().get(id) ?: return
        val keys = current.enabledProcesses.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (current.primaryProcess.isNotBlank()) keys.add(current.primaryProcess)
        keys.add(process)
        val now = clock()
        database().workspaces().upsert(
            current.copy(enabledProcesses = keys.joinToString(","), sync = current.sync.copy(updatedAt = now)),
        )
        audit(id, "workspaces", id, "upsert")
    }

    suspend fun softDeleteWorkspace(id: String) {
        val current = database().workspaces().get(id) ?: return
        val now = clock()
        database().workspaces().upsert(current.copy(sync = current.sync.copy(updatedAt = now, deletedAt = now)))
        audit(id, "workspaces", id, "delete")
    }

    fun observeContacts(workspaceId: String, query: String, kind: ContactKind?): Flow<List<Contact>> =
        database().contacts().observe(workspaceId).map { rows ->
            rows.map { it.toModel() }
                .filter { kind == null || it.kind == kind }
                .filter { TextFold.matches(query, it.fullName, it.phone, it.email, it.company, it.note, it.source) }
                .sortedByDescending { it.updatedAt }
        }

    fun observeContact(id: String): Flow<Contact?> =
        database().contacts().observeById(id).map { row -> row?.toModel()?.takeIf { it.deletedAt == null } }

    suspend fun saveContact(contact: Contact) {
        val existing = database().contacts().get(contact.id)
        val now = clock()
        val created = existing?.sync?.createdAt ?: now
        val search = TextFold.fold(listOf(contact.fullName, contact.phone, contact.email, contact.company, contact.note).joinToString(" "))
        database().contacts().upsert(
            ContactEntity(
                id = contact.id,
                sync = stamp(contact.workspaceId, created, now),
                fullName = contact.fullName.trim(),
                phone = contact.phone.trim(),
                email = contact.email.trim(),
                company = contact.company.trim(),
                kind = contact.kind.name,
                source = contact.source.trim(),
                note = contact.note.trim(),
                searchText = search,
            ),
        )
        audit(contact.workspaceId, "contacts", contact.id, "upsert")
    }

    suspend fun softDeleteContact(id: String) {
        val existing = database().contacts().get(id) ?: return
        val now = clock()
        database().contacts().softDelete(id, now)
        audit(existing.sync.workspaceId, "contacts", id, "delete")
    }

    fun observeEntities(
        workspaceId: String,
        typeKey: String,
        query: String,
        stageKey: String?,
        fieldEquals: Map<String, String>,
    ): Flow<List<EntityRow>> {
        val folded = TextFold.fold(query.trim()).replace("%", "").replace("_", "")
        return database().entities().observe(workspaceId, typeKey, folded, stageKey).map { rows ->
            val matched = if (fieldEquals.isEmpty()) {
                rows
            } else {
                val values = database().entities().valuesFor(rows.map { it.id })
                val byEntity = values.groupBy { it.entityId }
                rows.filter { row ->
                    val bag = byEntity[row.id].orEmpty()
                    fieldEquals.all { (key, expected) ->
                        bag.any { it.fieldKey == key && it.valueText == expected }
                    }
                }
            }
            matched.map { it.toRow() }
        }
    }

    suspend fun getContact(id: String): Contact? =
        database().contacts().get(id)?.let { row ->
            Contact(
                row.id, row.sync.workspaceId, row.fullName, row.phone, row.email, row.company,
                runCatching { ContactKind.valueOf(row.kind) }.getOrDefault(ContactKind.OTHER),
                row.source, row.note, row.sync.createdAt, row.sync.updatedAt, row.sync.deletedAt,
            )
        }?.takeIf { it.deletedAt == null }

    fun observeLinked(workspaceId: String, typeKey: String, relationKey: String, targetId: String): Flow<List<EntityRow>> =
        observeEntities(workspaceId, typeKey, "", null, emptyMap()).map { rows ->
            rows.filter { database().entities().relations(it.id).any { relation -> relation.relationKey == relationKey && relation.toId == targetId } }
        }

    suspend fun getEntity(id: String): EntityRow? =
        database().entities().get(id)?.takeIf { it.sync.deletedAt == null }?.toRow()

    suspend fun entityFields(entityId: String): Map<String, String> =
        database().entities().values(entityId).associate { value ->
            value.fieldKey to (value.valueText ?: value.valueNumber?.toString() ?: value.valueMoney?.toString()
                ?: value.valueDate?.toString() ?: value.valueBool?.toString() ?: value.valueRef ?: value.valueJson.orEmpty())
        }

    suspend fun entityRelations(entityId: String): Map<String, String> =
        database().entities().relations(entityId).associate { it.relationKey to it.toId }

    suspend fun saveEntity(
        workspaceId: String,
        id: String,
        type: EntityTypeDef,
        title: String,
        stageKey: String?,
        fields: Map<String, String>,
        relations: Map<String, String?>,
    ) {
        val existing = database().entities().get(id)
        val now = clock()
        val created = existing?.sync?.createdAt ?: now
        val search = TextFold.fold(buildString {
            append(title)
            append(' ')
            fields.values.forEach { append(it).append(' ') }
        })
        val badgeKey = type.listBadge
        val moneyKey = type.listMoney
        database().withTransaction {
        database().entities().upsert(
            EntityRecord(
                id = id,
                sync = stamp(workspaceId, created, now),
                entityTypeKey = type.key,
                title = title.trim(),
                searchText = search,
                pipelineKey = type.pipeline?.key,
                stageKey = stageKey,
                subtitle = type.listSubtitle.mapNotNull { key -> fields[key]?.takeIf { it.isNotBlank() } }.joinToString(", "),
                badge = fields[badgeKey].orEmpty(),
                moneyAmount = fields[moneyKey]?.toLongOrNull() ?: 0L,
                moneyCurrency = fields["currency"].orEmpty().ifBlank { "ILS" },
            ),
        )
        fault("entity-row")
        type.fields.forEach { field ->
            val raw = fields[field.key].orEmpty()
            database().entities().upsertValue(
                FieldValueEntity(
                    id = "$id:${field.key}",
                    sync = stamp(workspaceId, created, now),
                    entityId = id,
                    fieldKey = field.key,
                    valueText = raw.ifBlank { null },
                    valueNumber = if (field.fieldType() == FieldType.NUMBER) raw.replace(',', '.').toDoubleOrNull() else null,
                    valueMoney = if (field.fieldType() == FieldType.MONEY) raw.toLongOrNull() else null,
                    valueDate = if (field.fieldType() == FieldType.DATE) raw.toLongOrNull() else null,
                    valueBool = if (field.fieldType() == FieldType.BOOLEAN) raw.toBooleanStrictOrNull() else null,
                    valueRef = if (field.fieldType() == FieldType.REFERENCE) raw.ifBlank { null } else null,
                    valueJson = if (field.fieldType() == FieldType.MULTI || field.fieldType() == FieldType.FILE) raw.ifBlank { null } else null,
                ),
            )
        }
        database().entities().clearRelations(id, now)
        relations.forEach { (key, target) ->
            if (!target.isNullOrBlank()) {
                val relation = type.relations.firstOrNull { it.key == key }
                database().entities().upsertRelation(
                    RelationEntity(
                        id = Ids.new(),
                        sync = stamp(workspaceId, now, now),
                        fromId = id,
                        toId = target,
                        toKind = if (relation?.target == "contact") "contact" else "entity",
                        relationKey = key,
                    ),
                )
            }
        }
        applyLessonRules(workspaceId, id, type, title, stageKey, fields, relations, existing == null, created, now)
        audit(workspaceId, "entities", id, "upsert")
        }
    }

    private suspend fun applyLessonRules(
        workspaceId: String,
        id: String,
        type: EntityTypeDef,
        title: String,
        stageKey: String?,
        fields: Map<String, String>,
        relations: Map<String, String?>,
        isNew: Boolean,
        created: Long,
        now: Long,
    ) {
        if (isNew && type.behaviors.contains("weekly_series") && fields["recurrence"] == "weekly" && fields["series_id"].isNullOrBlank()) {
            database().entities().upsertValue(textValue(workspaceId, id, "series_id", id, created, now))
            val start = fields["starts_at"]?.toLongOrNull() ?: now
            LessonSeries.weeklyStarts(start).forEach { whenStart ->
                saveEntity(
                    workspaceId,
                    Ids.new(),
                    type,
                    title,
                    stageKey,
                    fields + mapOf(
                        "series_id" to id,
                        "starts_at" to whenStart.toString(),
                        "status" to "planned",
                    ),
                    relations,
                )
            }
        }
        if (type.behaviors.contains("package_deduction")) {
            reconcilePackage(
                workspaceId,
                id,
                fields["status"],
                fields["payment_status"],
                relations["student"] ?: relations["client"],
                deleted = false,
                created,
                now,
            )
        }
        syncVisitReminder(workspaceId, id, type, title, fields, relations, created, now)
    }

    private suspend fun syncVisitReminder(
        workspaceId: String,
        id: String,
        type: EntityTypeDef,
        title: String,
        fields: Map<String, String>,
        relations: Map<String, String?>,
        created: Long,
        now: Long,
    ) {
        if (!type.behaviors.contains(AppointmentEngine.REMINDER)) return
        val taskId = "visit-$id"
        val whenAt = fields["remind_at"]?.toLongOrNull()
        val contact = relations["student"] ?: relations["client"]
        if (whenAt == null) {
            database().tasks().softDelete(taskId, now)
            database().misc().softDeleteEvent("cal-$taskId", now)
            return
        }
        val task = TaskItem(taskId, workspaceId, title.trim(), "", whenAt, whenAt, null, contact, id, created, now)
        database().tasks().upsert(
            TaskEntity(
                id = taskId,
                sync = stamp(workspaceId, created, now),
                title = task.title,
                details = "",
                dueAt = whenAt,
                reminderAt = whenAt,
                completedAt = null,
                contactId = contact,
                entityId = id,
            ),
        )
        syncCalendar(task, created, now)
    }

    private suspend fun reconcilePackage(
        workspaceId: String,
        lessonId: String,
        status: String?,
        payment: String?,
        student: String?,
        deleted: Boolean,
        created: Long,
        now: Long,
    ) {
        val chargedTo = entityFields(lessonId)[PackageCharge.CHARGED_STUDENT]
        val balances = listOfNotNull(chargedTo, student).filter { it.isNotBlank() }.distinct()
            .associateWith { packageBalance(it) }
        val (nextCharged, deltas) = PackageCharge.reconcile(
            chargedTo,
            PackageCharge.counts(status, payment, student, deleted),
            student,
        ) { who -> balances[who] ?: 0 }
        deltas.forEach { (who, delta) ->
            saveContactExtras(workspaceId, who, mapOf("package_balance" to (packageBalance(who) + delta).toString()))
        }
        if (nextCharged.isNullOrBlank()) {
            database().entities().deleteValue(lessonId, PackageCharge.CHARGED_STUDENT)
        } else {
            database().entities().upsertValue(
                textValue(workspaceId, lessonId, PackageCharge.CHARGED_STUDENT, nextCharged, created, now),
            )
        }
    }

    private suspend fun packageBalance(contactId: String): Int =
        contactExtras(contactId)["package_balance"]?.toDoubleOrNull()?.toInt() ?: 0

    private fun textValue(workspaceId: String, entityId: String, key: String, value: String, created: Long, now: Long) =
        FieldValueEntity(
            id = "$entityId:$key",
            sync = stamp(workspaceId, created, now),
            entityId = entityId,
            fieldKey = key,
            valueText = value,
            valueNumber = null,
            valueMoney = null,
            valueDate = null,
            valueBool = null,
            valueRef = null,
            valueJson = null,
        )

    suspend fun softDeleteEntity(id: String) {
        val existing = database().entities().get(id) ?: return
        if (existing.sync.deletedAt != null) return
        val now = clock()
        database().withTransaction {
            val fields = entityFields(id)
            val student = entityRelations(id)["student"] ?: entityRelations(id)["client"]
            database().entities().softDelete(id, now)
            database().tasks().softDelete("visit-$id", now)
            database().misc().softDeleteEvent("cal-visit-$id", now)
            reconcilePackage(
                existing.sync.workspaceId,
                id,
                fields["status"],
                fields["payment_status"],
                student,
                deleted = true,
                existing.sync.createdAt,
                now,
            )
            audit(existing.sync.workspaceId, "entities", id, "delete")
        }
    }

    suspend fun restoreEntity(id: String) {
        val existing = database().entities().get(id) ?: return
        val now = clock()
        database().withTransaction {
            database().entities().upsert(existing.copy(sync = existing.sync.copy(updatedAt = now, deletedAt = null)))
            val fields = entityFields(id)
            val student = entityRelations(id)["student"] ?: entityRelations(id)["client"]
            reconcilePackage(
                existing.sync.workspaceId,
                id,
                fields["status"],
                fields["payment_status"],
                student,
                deleted = false,
                existing.sync.createdAt,
                now,
            )
            audit(existing.sync.workspaceId, "entities", id, "upsert")
        }
    }

    fun observeTasks(workspaceId: String, filter: TaskFilter, query: String): Flow<List<TaskItem>> =
        database().tasks().observe(workspaceId).map { rows ->
            val now = clock()
            rows.map { it.toModel() }
                .filter { task ->
                    when (filter) {
                        TaskFilter.ALL -> true
                        TaskFilter.OPEN -> task.completedAt == null
                        TaskFilter.DONE -> task.completedAt != null
                        TaskFilter.TODAY -> task.completedAt == null && DayWindow.isDueToday(task.dueAt, now, zone)
                        TaskFilter.OVERDUE -> DayWindow.isOverdue(task.dueAt, task.completedAt, now, zone)
                    }
                }
                .filter { TextFold.matches(query, it.title, it.details) }
                .sortedWith(
                    if (filter == TaskFilter.DONE) {
                        compareByDescending { it.completedAt ?: 0L }
                    } else {
                        compareBy<TaskItem> { if (DayWindow.isOverdue(it.dueAt, it.completedAt, now, zone)) 0 else 1 }
                            .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    },
                )
        }

    suspend fun getTask(id: String): TaskItem? = database().tasks().get(id)?.toModel()?.takeIf { it.deletedAt == null }

    suspend fun saveTask(task: TaskItem, schedule: (TaskItem) -> Unit) {
        val existing = database().tasks().get(task.id)
        val now = clock()
        val created = existing?.sync?.createdAt ?: now
        val stored = task.copy(createdAt = created, updatedAt = now, deletedAt = null)
        database().withTransaction {
        database().tasks().upsert(
            TaskEntity(
                id = stored.id,
                sync = stamp(stored.workspaceId, created, now),
                title = stored.title.trim(),
                details = stored.details.trim(),
                dueAt = stored.dueAt,
                reminderAt = stored.reminderAt,
                completedAt = stored.completedAt,
                contactId = stored.contactId,
                entityId = stored.entityId,
            ),
        )
        fault("task-row")
        syncCalendar(stored, created, now)
        audit(stored.workspaceId, "tasks", stored.id, "upsert")
        }
        schedule(stored)
    }

    private suspend fun syncCalendar(task: TaskItem, created: Long, now: Long) {
        val calendarId = "cal-${task.id}"
        if (task.dueAt == null || task.deletedAt != null) {
            database().misc().softDeleteEvent(calendarId, now)
            return
        }
        database().misc().upsertEvent(
            CalendarEntity(
                id = calendarId,
                sync = stamp(task.workspaceId, created, now),
                title = task.title,
                startsAt = task.dueAt,
                endsAt = null,
                contactId = task.contactId,
                entityId = task.entityId,
            ),
        )
    }

    suspend fun setTaskCompleted(id: String, completed: Boolean, schedule: (TaskItem) -> Unit) {
        val task = getTask(id) ?: return
        val now = clock()
        saveTask(task.copy(completedAt = if (completed) now else null), schedule)
    }

    suspend fun softDeleteTask(id: String, cancel: (String) -> Unit) {
        val existing = database().tasks().get(id) ?: return
        val now = clock()
        database().withTransaction {
            database().tasks().softDelete(id, now)
            database().misc().softDeleteEvent("cal-$id", now)
            audit(existing.sync.workspaceId, "tasks", id, "delete")
        }
        cancel(id)
    }

    suspend fun contactExtras(contactId: String): Map<String, String> = entityFields(contactId)

    suspend fun saveContactExtras(workspaceId: String, contactId: String, extras: Map<String, String>) {
        val now = clock()
        extras.forEach { (key, value) ->
            database().entities().upsertValue(
                FieldValueEntity(
                    id = "$contactId:$key",
                    sync = stamp(workspaceId, now, now),
                    entityId = contactId,
                    fieldKey = key,
                    valueText = value,
                    valueNumber = value.replace(',', '.').toDoubleOrNull(),
                    valueMoney = value.toLongOrNull(),
                    valueDate = null,
                    valueBool = null,
                    valueRef = null,
                    valueJson = null,
                ),
            )
        }
    }

    suspend fun reminderTasks(): List<TaskItem> = database().tasks().reminders().map { it.toModel() }

    suspend fun reminderCandidates(): List<TaskItem> = database().tasks().reminderCandidates().map { it.toModel() }

    fun observeActivities(contactId: String?, entityId: String?): Flow<List<ActivityEntry>> = when {
        contactId != null -> database().activities().forContact(contactId).map { rows -> rows.map { it.toModel() } }
        entityId != null -> database().activities().forEntity(entityId).map { rows -> rows.map { it.toModel() } }
        else -> flow { emit(emptyList()) }
    }

    suspend fun addActivity(workspaceId: String, kind: String, body: String, contactId: String?, entityId: String?) {
        val now = clock()
        val id = Ids.new()
        database().activities().upsert(
            ActivityEntity(id, stamp(workspaceId, now, now), kind, body.trim(), contactId, entityId),
        )
        audit(workspaceId, "activities", id, "upsert")
    }

    fun observeDocuments(ownerId: String): Flow<List<DocumentFile>> =
        database().misc().documents(ownerId).map { rows ->
            rows.map {
                DocumentFile(it.id, it.sync.workspaceId, it.ownerId, it.fileName, it.mime, it.title, it.sortOrder)
            }
        }

    suspend fun addDocument(workspaceId: String, ownerId: String, fileName: String, title: String) {
        val now = clock()
        val sort = database().misc().maxSort(ownerId) + 1
        val id = Ids.new()
        database().misc().upsertDocument(
            DocumentEntity(id, stamp(workspaceId, now, now), ownerId, fileName, "image/jpeg", title, sort),
        )
        audit(workspaceId, "documents", id, "upsert")
    }

    suspend fun softDeleteDocument(id: String, workspaceId: String, fileName: String, photos: PhotoStore) {
        val now = clock()
        database().misc().softDeleteDocument(id, now)
        photos.delete(fileName)
        audit(workspaceId, "documents", id, "delete")
    }

    fun observePayments(entityId: String): Flow<List<PaymentRecord>> =
        database().misc().payments(entityId).map { rows ->
            rows.map {
                PaymentRecord(
                    it.id,
                    it.sync.workspaceId,
                    PaymentKind.valueOf(it.kind),
                    it.amount,
                    it.currency,
                    it.occurredAt,
                    it.contactId,
                    it.entityId,
                    it.note,
                )
            }
        }

    suspend fun addPayment(workspaceId: String, kind: PaymentKind, amount: Long, currency: String, contactId: String?, entityId: String?, note: String) {
        val now = clock()
        val id = Ids.new()
        database().misc().upsertPayment(
            PaymentEntity(id, stamp(workspaceId, now, now), kind.name, amount, currency, now, contactId, entityId, note),
        )
        audit(workspaceId, "payments", id, "upsert")
    }

    fun observeCatalog(workspaceId: String): Flow<List<CatalogItem>> =
        database().misc().catalog(workspaceId).map { rows ->
            rows.map { CatalogItem(it.id, it.sync.workspaceId, it.name, it.price, it.currency, it.unit) }
        }

    suspend fun addCatalog(workspaceId: String, name: String, price: Long, currency: String, unit: String) {
        val now = clock()
        val id = Ids.new()
        database().misc().upsertCatalog(CatalogEntity(id, stamp(workspaceId, now, now), name.trim(), price, currency, unit))
        audit(workspaceId, "catalog_items", id, "upsert")
    }

    fun observeEvents(workspaceId: String): Flow<List<CalendarEvent>> =
        database().misc().events(workspaceId).map { rows ->
            rows.map { CalendarEvent(it.id, it.sync.workspaceId, it.title, it.startsAt, it.endsAt, it.contactId, it.entityId) }
        }

    fun observeMessages(contactId: String): Flow<List<MessageEntry>> =
        database().misc().messages(contactId).map { rows -> rows.map { it.toModel() } }

    suspend fun logMessage(workspaceId: String, contactId: String, messengerId: String, body: String, status: MessageStatus): String {
        val now = clock()
        val id = Ids.new()
        database().misc().upsertMessage(
            MessageEntity(id, stamp(workspaceId, now, now), contactId, messengerId, body, status.name),
        )
        audit(workspaceId, "message_log", id, "upsert")
        return id
    }

    suspend fun updateMessageStatus(id: String, status: MessageStatus) {
        val existing = database().misc().message(id) ?: return
        val now = clock()
        database().misc().upsertMessage(existing.copy(status = status.name, sync = existing.sync.copy(updatedAt = now, updatedByUserId = userId)))
        audit(existing.sync.workspaceId, "message_log", id, "upsert")
    }

    suspend fun changesSince(workspaceId: String, since: Long): List<Change> =
        database().misc().changesSince(workspaceId, since).map {
            Change(it.id, it.sync.workspaceId, it.tableName, it.subjectId, it.operation, it.changedAt)
        }

    fun observeHome(workspaceId: String, template: TemplateFile): Flow<HomeSummary> {
        val listingType = template.entityTypes.firstOrNull { it.key == "property" }?.key
        val dealType = template.entityTypes.firstOrNull { it.pipeline != null }?.key
        val stages = template.entityTypes.firstOrNull { it.key == dealType }?.pipeline?.stages.orEmpty()
        val contacts = observeContacts(workspaceId, "", null)
        val listings = if (listingType == null) flow { emit(emptyList()) } else observeEntities(workspaceId, listingType, "", null, emptyMap())
        val deals = if (dealType == null) flow { emit(emptyList()) } else observeEntities(workspaceId, dealType, "", null, emptyMap())
        val tasks = observeTasks(workspaceId, TaskFilter.ALL, "")
        return combine(contacts, listings, deals, tasks) { c, l, d, t ->
            if (template.dashboard == "realtor") {
                HomeCalculator.summarize(c, l, d, t, stages, "active", clock(), zone)
            } else {
                HomeSummary(
                    contacts = c.size,
                    activeListings = l.size + d.size,
                    openDeals = 0,
                    dueToday = t.count { it.completedAt == null && DayWindow.isDueToday(it.dueAt, clock(), zone) },
                    overdue = t.count { DayWindow.isOverdue(it.dueAt, it.completedAt, clock(), zone) },
                    stages = emptyList(),
                    upcoming = t.filter { it.completedAt == null }.take(5),
                    recentDeals = d.take(3),
                )
            }
        }
    }

    /**
     * Times a search over the current workspace. Used by the debug benchmark.
     * Returns the number of hits and the elapsed milliseconds.
     */
    suspend fun benchmarkSearch(workspaceId: String, typeKey: String, query: String): Pair<Int, Long> {
        val folded = TextFold.fold(query.trim()).replace("%", "").replace("_", "")
        val started = System.nanoTime()
        val count = database().entities().count(workspaceId, typeKey, folded)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        return count to elapsed
    }

    private suspend fun audit(workspaceId: String, table: String, subjectId: String, operation: String) {
        val now = clock()
        database().misc().upsertChange(
            ChangeLogEntity(
                id = Ids.new(),
                sync = stamp(workspaceId, now, now),
                tableName = table,
                subjectId = subjectId,
                operation = operation,
                changedAt = now,
            ),
        )
    }

    /** Deletes only the listed records. Used for demo data, never for a whole workspace. */
    suspend fun eraseOwned(ids: Set<String>) {
        if (ids.isEmpty()) return
        val db = database().openHelper.writableDatabase
        database().withTransaction {
            val tables = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            fun run(table: String, sql: String, args: Array<String>) {
                if (table in tables) db.execSQL(sql, args)
            }
            for (id in ids) {
                run("field_values", "DELETE FROM field_values WHERE entity_id = ? OR id LIKE ?", arrayOf(id, "$id:%"))
                run("entity_relations", "DELETE FROM entity_relations WHERE from_id = ? OR to_id = ?", arrayOf(id, id))
                run("tasks", "DELETE FROM tasks WHERE id = ? OR contact_id = ? OR entity_id = ?", arrayOf(id, id, id))
                run("calendar_events", "DELETE FROM calendar_events WHERE contact_id = ? OR entity_id = ? OR id = ?", arrayOf(id, id, "cal-$id"))
                run("payments", "DELETE FROM payments WHERE contact_id = ? OR entity_id = ?", arrayOf(id, id))
                run("documents", "DELETE FROM documents WHERE owner_id = ? OR id = ?", arrayOf(id, id))
                run("activities", "DELETE FROM activities WHERE contact_id = ? OR entity_id = ? OR id = ?", arrayOf(id, id, id))
                run("message_log", "DELETE FROM message_log WHERE contact_id = ? OR id = ?", arrayOf(id, id))
                run("contacts", "DELETE FROM contacts WHERE id = ?", arrayOf(id))
                run("entities", "DELETE FROM entities WHERE id = ?", arrayOf(id))
            }
        }
    }

    private fun stamp(workspaceId: String, createdAt: Long, updatedAt: Long): SyncStamp =
        SyncStamp(workspaceId, userId, userId, userId, null, createdAt, updatedAt, null)

    private fun WorkspaceEntity.toModel() = Workspace(
        id, name, templateId, templateVersion, country, sync.createdAt, sync.updatedAt, sync.deletedAt,
        category, primaryProcess, enabledProcesses,
    )

    private fun ContactEntity.toModel() = Contact(
        id, sync.workspaceId, fullName, phone, email, company,
        runCatching { ContactKind.valueOf(kind) }.getOrDefault(ContactKind.OTHER),
        source, note, sync.createdAt, sync.updatedAt, sync.deletedAt,
    )

    private fun EntityRecord.toRow() = EntityRow(
        id, sync.workspaceId, entityTypeKey, title, stageKey, subtitle, badge, moneyAmount, moneyCurrency, sync.updatedAt, sync.deletedAt,
    )

    private fun TaskEntity.toModel() = TaskItem(
        id, sync.workspaceId, title, details, dueAt, reminderAt, completedAt, contactId, entityId, sync.createdAt, sync.updatedAt, sync.deletedAt,
    )

    private fun ActivityEntity.toModel() = ActivityEntry(id, sync.workspaceId, kind, body, contactId, entityId, sync.createdAt)

    private fun MessageEntity.toModel() = MessageEntry(
        id, sync.workspaceId, contactId, messengerId, body,
        runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.PREPARED),
        sync.createdAt,
    )
}
