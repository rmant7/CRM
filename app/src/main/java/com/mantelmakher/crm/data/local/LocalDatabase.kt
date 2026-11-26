package com.mantelmakher.crm.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.mantelmakher.crm.domain.model.ActivityKind
import com.mantelmakher.crm.domain.model.ClientKind
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.ListingPurpose
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.domain.model.PropertyType
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "clients",
    indices = [Index("deletedAt"), Index("updatedAt")],
)
data class ClientEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val email: String,
    val company: String,
    val kind: ClientKind,
    val source: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "properties",
    indices = [Index("deletedAt"), Index("status"), Index("updatedAt")],
)
data class PropertyEntity(
    @PrimaryKey val id: String,
    val title: String,
    val purpose: ListingPurpose,
    val type: PropertyType,
    val status: PropertyStatus,
    val address: String,
    val city: String,
    val price: Long,
    val currency: String,
    val areaSqm: Double,
    val rooms: Double,
    val floor: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "property_photos",
    indices = [Index("propertyId"), Index("deletedAt")],
)
data class PropertyPhotoEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val fileName: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "deals",
    indices = [Index("deletedAt"), Index("clientId"), Index("propertyId"), Index("stage")],
)
data class DealEntity(
    @PrimaryKey val id: String,
    val title: String,
    val clientId: String?,
    val propertyId: String?,
    val stage: DealStage,
    val amount: Long,
    val currency: String,
    val expectedCloseAt: Long?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "tasks",
    indices = [Index("deletedAt"), Index("dueAt"), Index("clientId"), Index("dealId"), Index("propertyId")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val details: String,
    val dueAt: Long?,
    val reminderAt: Long?,
    val completedAt: Long?,
    val clientId: String?,
    val dealId: String?,
    val propertyId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "activities",
    indices = [Index("deletedAt"), Index("clientId"), Index("dealId"), Index("propertyId"), Index("createdAt")],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val kind: ActivityKind,
    val body: String,
    val clientId: String?,
    val dealId: String?,
    val propertyId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

class EnumConverters {
    @TypeConverter fun fromClientKind(value: ClientKind): String = value.name
    @TypeConverter fun toClientKind(value: String): ClientKind = ClientKind.valueOf(value)

    @TypeConverter fun fromPurpose(value: ListingPurpose): String = value.name
    @TypeConverter fun toPurpose(value: String): ListingPurpose = ListingPurpose.valueOf(value)

    @TypeConverter fun fromPropertyType(value: PropertyType): String = value.name
    @TypeConverter fun toPropertyType(value: String): PropertyType = PropertyType.valueOf(value)

    @TypeConverter fun fromPropertyStatus(value: PropertyStatus): String = value.name
    @TypeConverter fun toPropertyStatus(value: String): PropertyStatus = PropertyStatus.valueOf(value)

    @TypeConverter fun fromDealStage(value: DealStage): String = value.name
    @TypeConverter fun toDealStage(value: String): DealStage = DealStage.valueOf(value)

    @TypeConverter fun fromActivityKind(value: ActivityKind): String = value.name
    @TypeConverter fun toActivityKind(value: String): ActivityKind = ActivityKind.valueOf(value)
}

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    fun observeById(id: String): Flow<ClientEntity?>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun get(id: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClientEntity)

    @Query("UPDATE clients SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)
}

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE id = :id")
    fun observeById(id: String): Flow<PropertyEntity?>

    @Query("SELECT * FROM properties WHERE id = :id")
    suspend fun get(id: String): PropertyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PropertyEntity)

    @Query("UPDATE properties SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)
}

@Dao
interface PhotoDao {
    @Query(
        "SELECT * FROM property_photos WHERE propertyId = :propertyId AND deletedAt IS NULL " +
            "ORDER BY sortOrder ASC, createdAt ASC",
    )
    fun observeForProperty(propertyId: String): Flow<List<PropertyPhotoEntity>>

    @Query("SELECT * FROM property_photos WHERE id = :id")
    suspend fun get(id: String): PropertyPhotoEntity?

    @Query(
        "SELECT COALESCE(MAX(sortOrder), -1) FROM property_photos " +
            "WHERE propertyId = :propertyId AND deletedAt IS NULL",
    )
    suspend fun maxSort(propertyId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PropertyPhotoEntity)

    @Query("UPDATE property_photos SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)
}

@Dao
interface DealDao {
    @Query("SELECT * FROM deals WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<DealEntity>>

    @Query("SELECT * FROM deals WHERE id = :id")
    fun observeById(id: String): Flow<DealEntity?>

    @Query("SELECT * FROM deals WHERE id = :id")
    suspend fun get(id: String): DealEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DealEntity)

    @Query("UPDATE deals SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND completedAt IS NULL AND reminderAt IS NOT NULL",
    )
    suspend fun reminderCandidates(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)
}

@Dao
interface ActivityDao {
    @Query(
        "SELECT * FROM activities WHERE deletedAt IS NULL AND clientId = :clientId ORDER BY createdAt DESC",
    )
    fun observeForClient(clientId: String): Flow<List<ActivityEntity>>

    @Query(
        "SELECT * FROM activities WHERE deletedAt IS NULL AND dealId = :dealId ORDER BY createdAt DESC",
    )
    fun observeForDeal(dealId: String): Flow<List<ActivityEntity>>

    @Query(
        "SELECT * FROM activities WHERE deletedAt IS NULL AND propertyId = :propertyId ORDER BY createdAt DESC",
    )
    fun observeForProperty(propertyId: String): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActivityEntity)

    @Query("UPDATE activities SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)
}

@Database(
    entities = [
        ClientEntity::class,
        PropertyEntity::class,
        PropertyPhotoEntity::class,
        DealEntity::class,
        TaskEntity::class,
        ActivityEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class CrmDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun propertyDao(): PropertyDao
    abstract fun photoDao(): PhotoDao
    abstract fun dealDao(): DealDao
    abstract fun taskDao(): TaskDao
    abstract fun activityDao(): ActivityDao

    companion object {
        const val NAME = "mantel_crm.db"

        fun create(context: Context): CrmDatabase =
            Room.databaseBuilder(context.applicationContext, CrmDatabase::class.java, NAME)
                .build()
    }
}
