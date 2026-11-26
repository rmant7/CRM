package com.intelliverse.croman.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Sync columns present on every record so a later LAN peer or cloud backend
 * can exchange the same rows. v0.1 lived in another applicationId, so this
 * database starts at version 1. Later schema changes need a real Migration.
 */
data class SyncStamp(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String,
    @ColumnInfo(name = "created_by_user_id") val createdByUserId: String,
    @ColumnInfo(name = "updated_by_user_id") val updatedByUserId: String,
    @ColumnInfo(name = "assigned_to_user_id") val assignedToUserId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

@Entity(tableName = "workspaces", indices = [Index("deleted_at"), Index("template_id")])
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    val name: String,
    @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "template_version") val templateVersion: Int,
    val country: String,
)

@Entity(tableName = "entity_types", indices = [Index("workspace_id"), Index("type_key")])
data class EntityTypeEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "type_key") val typeKey: String,
    @ColumnInfo(name = "label_en") val labelEn: String,
    @ColumnInfo(name = "label_ru") val labelRu: String,
    @ColumnInfo(name = "label_he") val labelHe: String,
    @ColumnInfo(name = "has_photos") val hasPhotos: Boolean,
    @ColumnInfo(name = "pipeline_key") val pipelineKey: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "list_badge") val listBadge: String?,
    @ColumnInfo(name = "list_money") val listMoney: String?,
    @ColumnInfo(name = "list_subtitle") val listSubtitle: String,
    @ColumnInfo(name = "definition_json") val definitionJson: String,
)

@Entity(
    tableName = "field_definitions",
    indices = [Index("workspace_id"), Index(value = ["workspace_id", "entity_type_key", "field_key"], unique = true)],
)
data class FieldDefinitionEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "entity_type_key") val entityTypeKey: String,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    @ColumnInfo(name = "label_en") val labelEn: String,
    @ColumnInfo(name = "label_ru") val labelRu: String,
    @ColumnInfo(name = "label_he") val labelHe: String,
    @ColumnInfo(name = "field_type") val fieldType: String,
    val required: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "options_json") val optionsJson: String,
    @ColumnInfo(name = "reference_type") val referenceType: String?,
)

@Entity(tableName = "pipelines", indices = [Index("workspace_id"), Index("pipeline_key")])
data class PipelineEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "pipeline_key") val pipelineKey: String,
    @ColumnInfo(name = "entity_type_key") val entityTypeKey: String,
)

@Entity(
    tableName = "pipeline_stages",
    indices = [Index("workspace_id"), Index(value = ["workspace_id", "pipeline_key", "stage_key"])],
)
data class StageEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "pipeline_key") val pipelineKey: String,
    @ColumnInfo(name = "stage_key") val stageKey: String,
    @ColumnInfo(name = "label_en") val labelEn: String,
    @ColumnInfo(name = "label_ru") val labelRu: String,
    @ColumnInfo(name = "label_he") val labelHe: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "closed_won") val closedWon: Boolean,
    @ColumnInfo(name = "closed_lost") val closedLost: Boolean,
)

@Entity(
    tableName = "contacts",
    indices = [Index("workspace_id"), Index("deleted_at"), Index("search_text"), Index("updated_at")],
)
data class ContactEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "full_name") val fullName: String,
    val phone: String,
    val email: String,
    val company: String,
    val kind: String,
    val source: String,
    val note: String,
    @ColumnInfo(name = "search_text") val searchText: String,
)

@Entity(
    tableName = "entities",
    indices = [
        Index("workspace_id"),
        Index("deleted_at"),
        Index("entity_type_key"),
        Index("stage_key"),
        Index("search_text"),
        Index("updated_at"),
        Index("money_amount"),
    ],
)
data class EntityRecord(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "entity_type_key") val entityTypeKey: String,
    val title: String,
    @ColumnInfo(name = "search_text") val searchText: String,
    @ColumnInfo(name = "pipeline_key") val pipelineKey: String?,
    @ColumnInfo(name = "stage_key") val stageKey: String?,
    val subtitle: String,
    val badge: String,
    @ColumnInfo(name = "money_amount") val moneyAmount: Long,
    @ColumnInfo(name = "money_currency") val moneyCurrency: String,
)

@Entity(
    tableName = "field_values",
    indices = [
        Index(value = ["entity_id", "field_key"], unique = true),
        Index("workspace_id"),
        Index("deleted_at"),
        Index(value = ["field_key", "value_text"]),
        Index(value = ["field_key", "value_number"]),
        Index(value = ["field_key", "value_money"]),
        Index(value = ["field_key", "value_date"]),
    ],
)
data class FieldValueEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    @ColumnInfo(name = "value_text") val valueText: String?,
    @ColumnInfo(name = "value_number") val valueNumber: Double?,
    @ColumnInfo(name = "value_money") val valueMoney: Long?,
    @ColumnInfo(name = "value_date") val valueDate: Long?,
    @ColumnInfo(name = "value_bool") val valueBool: Boolean?,
    @ColumnInfo(name = "value_ref") val valueRef: String?,
    @ColumnInfo(name = "value_json") val valueJson: String?,
)

@Entity(tableName = "entity_relations", indices = [Index("workspace_id"), Index("from_id"), Index("to_id"), Index("relation_key")])
data class RelationEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "from_id") val fromId: String,
    @ColumnInfo(name = "to_id") val toId: String,
    @ColumnInfo(name = "to_kind") val toKind: String,
    @ColumnInfo(name = "relation_key") val relationKey: String,
)

@Entity(tableName = "tasks", indices = [Index("workspace_id"), Index("deleted_at"), Index("due_at"), Index("contact_id"), Index("entity_id")])
data class TaskEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    val title: String,
    val details: String,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "reminder_at") val reminderAt: Long?,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "contact_id") val contactId: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
)

@Entity(tableName = "activities", indices = [Index("workspace_id"), Index("contact_id"), Index("entity_id"), Index("created_at")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    val kind: String,
    val body: String,
    @ColumnInfo(name = "contact_id") val contactId: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
)

@Entity(tableName = "calendar_events", indices = [Index("workspace_id"), Index("starts_at")])
data class CalendarEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    val title: String,
    @ColumnInfo(name = "starts_at") val startsAt: Long,
    @ColumnInfo(name = "ends_at") val endsAt: Long?,
    @ColumnInfo(name = "contact_id") val contactId: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
)

@Entity(tableName = "payments", indices = [Index("workspace_id"), Index("entity_id"), Index("contact_id"), Index("kind")])
data class PaymentEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    val kind: String,
    val amount: Long,
    val currency: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "contact_id") val contactId: String?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
    val note: String,
)

@Entity(tableName = "catalog_items", indices = [Index("workspace_id"), Index("name")])
data class CatalogEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    val name: String,
    val price: Long,
    val currency: String,
    val unit: String,
)

@Entity(tableName = "documents", indices = [Index("workspace_id"), Index("owner_id")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val mime: String,
    val title: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(tableName = "message_log", indices = [Index("workspace_id"), Index("contact_id"), Index("status")])
data class MessageEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "messenger_id") val messengerId: String,
    val body: String,
    val status: String,
)

@Entity(tableName = "change_log", indices = [Index("workspace_id"), Index("changed_at"), Index("subject_id"), Index("table_name")])
data class ChangeLogEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    val operation: String,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)

@Entity(tableName = "view_configs", indices = [Index(value = ["workspace_id", "config_key"], unique = true)])
data class ViewConfigEntity(
    @PrimaryKey val id: String,
    @Embedded val sync: SyncStamp,
    @ColumnInfo(name = "config_key") val configKey: String,
    @ColumnInfo(name = "body_json") val bodyJson: String,
)

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observe(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun get(id: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkspaceEntity)
}

@Dao
interface ConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertType(entity: EntityTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertField(entity: FieldDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPipeline(entity: PipelineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStage(entity: StageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertView(entity: ViewConfigEntity)

    @Query("SELECT * FROM entity_types WHERE workspace_id = :workspaceId AND deleted_at IS NULL ORDER BY sort_order")
    fun observeTypes(workspaceId: String): Flow<List<EntityTypeEntity>>

    @Query("SELECT * FROM entity_types WHERE workspace_id = :workspaceId AND type_key = :key AND deleted_at IS NULL LIMIT 1")
    suspend fun type(workspaceId: String, key: String): EntityTypeEntity?

    @Query("SELECT * FROM field_definitions WHERE workspace_id = :workspaceId AND entity_type_key = :type AND deleted_at IS NULL ORDER BY sort_order")
    suspend fun fields(workspaceId: String, type: String): List<FieldDefinitionEntity>

    @Query("SELECT * FROM pipeline_stages WHERE workspace_id = :workspaceId AND pipeline_key = :pipeline AND deleted_at IS NULL ORDER BY sort_order")
    suspend fun stages(workspaceId: String, pipeline: String): List<StageEntity>

    @Query("SELECT * FROM field_definitions WHERE workspace_id = :workspaceId AND deleted_at IS NULL")
    suspend fun allFields(workspaceId: String): List<FieldDefinitionEntity>

    @Query("UPDATE field_definitions SET field_key = :toKey, updated_at = :now WHERE workspace_id = :workspaceId AND entity_type_key = :type AND field_key = :fromKey")
    suspend fun renameField(workspaceId: String, type: String, fromKey: String, toKey: String, now: Long)

    @Query("UPDATE field_values SET field_key = :toKey, updated_at = :now WHERE workspace_id = :workspaceId AND field_key = :fromKey")
    suspend fun renameValues(workspaceId: String, fromKey: String, toKey: String, now: Long)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE workspace_id = :workspaceId AND deleted_at IS NULL")
    fun observe(workspaceId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun get(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeById(id: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContactEntity)

    @Query("UPDATE contacts SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface EntityDao {
    @Query(
        "SELECT * FROM entities WHERE workspace_id = :workspaceId AND entity_type_key = :type " +
            "AND deleted_at IS NULL AND (:stage IS NULL OR stage_key = :stage) " +
            "AND (:query = '' OR search_text LIKE '%' || :query || '%') ORDER BY updated_at DESC",
    )
    fun observe(workspaceId: String, type: String, query: String, stage: String?): Flow<List<EntityRecord>>

    @Query(
        "SELECT COUNT(*) FROM entities WHERE workspace_id = :workspaceId AND entity_type_key = :type " +
            "AND deleted_at IS NULL AND (:query = '' OR search_text LIKE '%' || :query || '%')",
    )
    suspend fun count(workspaceId: String, type: String, query: String): Int

    @Query("SELECT id FROM entities WHERE workspace_id = :workspaceId AND entity_type_key = :type AND deleted_at IS NULL")
    suspend fun idsOf(workspaceId: String, type: String): List<String>

    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun get(id: String): EntityRecord?

    @Query("SELECT * FROM entities WHERE id = :id")
    fun observeById(id: String): Flow<EntityRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EntityRecord)

    @Query("UPDATE entities SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM field_values WHERE entity_id = :entityId AND deleted_at IS NULL")
    suspend fun values(entityId: String): List<FieldValueEntity>

    @Query("SELECT * FROM field_values WHERE entity_id IN (:ids) AND deleted_at IS NULL")
    suspend fun valuesFor(ids: List<String>): List<FieldValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertValue(entity: FieldValueEntity)

    @Query("DELETE FROM field_values WHERE entity_id = :entityId AND field_key = :key")
    suspend fun deleteValue(entityId: String, key: String)

    @Query("SELECT * FROM entity_relations WHERE from_id = :fromId AND deleted_at IS NULL")
    suspend fun relations(fromId: String): List<RelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelation(entity: RelationEntity)

    @Query("UPDATE entity_relations SET deleted_at = :now, updated_at = :now WHERE from_id = :fromId")
    suspend fun clearRelations(fromId: String, now: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE workspace_id = :workspaceId AND deleted_at IS NULL")
    fun observe(workspaceId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL AND completed_at IS NULL AND reminder_at IS NOT NULL")
    suspend fun reminders(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Query("UPDATE tasks SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE contact_id = :contactId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun forContact(contactId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE entity_id = :entityId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun forEntity(entityId: String): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActivityEntity)
}

@Dao
interface MiscDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(entity: DocumentEntity)

    @Query("SELECT * FROM documents WHERE owner_id = :ownerId AND deleted_at IS NULL ORDER BY sort_order")
    fun documents(ownerId: String): Flow<List<DocumentEntity>>

    @Query("UPDATE documents SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteDocument(id: String, now: Long)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM documents WHERE owner_id = :ownerId AND deleted_at IS NULL")
    suspend fun maxSort(ownerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(entity: PaymentEntity)

    @Query("SELECT * FROM payments WHERE entity_id = :entityId AND deleted_at IS NULL ORDER BY occurred_at DESC")
    fun payments(entityId: String): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalog(entity: CatalogEntity)

    @Query("SELECT * FROM catalog_items WHERE workspace_id = :workspaceId AND deleted_at IS NULL ORDER BY name")
    fun catalog(workspaceId: String): Flow<List<CatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(entity: CalendarEntity)

    @Query("SELECT * FROM calendar_events WHERE workspace_id = :workspaceId AND deleted_at IS NULL ORDER BY starts_at")
    fun events(workspaceId: String): Flow<List<CalendarEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: MessageEntity)

    @Query("SELECT * FROM message_log WHERE contact_id = :contactId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun messages(contactId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message_log WHERE id = :id")
    suspend fun message(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChange(entity: ChangeLogEntity)

    @Query("SELECT * FROM change_log WHERE workspace_id = :workspaceId AND changed_at > :since ORDER BY changed_at")
    suspend fun changesSince(workspaceId: String, since: Long): List<ChangeLogEntity>
}

@Database(
    entities = [
        WorkspaceEntity::class,
        EntityTypeEntity::class,
        FieldDefinitionEntity::class,
        PipelineEntity::class,
        StageEntity::class,
        ContactEntity::class,
        EntityRecord::class,
        FieldValueEntity::class,
        RelationEntity::class,
        TaskEntity::class,
        ActivityEntity::class,
        CalendarEntity::class,
        PaymentEntity::class,
        CatalogEntity::class,
        DocumentEntity::class,
        MessageEntity::class,
        ChangeLogEntity::class,
        ViewConfigEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CromanDatabase : RoomDatabase() {
    abstract fun workspaces(): WorkspaceDao
    abstract fun config(): ConfigDao
    abstract fun contacts(): ContactDao
    abstract fun entities(): EntityDao
    abstract fun tasks(): TaskDao
    abstract fun activities(): ActivityDao
    abstract fun misc(): MiscDao

    companion object {
        const val NAME = "croman.db"

        fun create(context: Context): CromanDatabase =
            Room.databaseBuilder(context.applicationContext, CromanDatabase::class.java, NAME).build()
    }
}
