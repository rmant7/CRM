package com.mantelmakher.crm.domain.model

import java.util.UUID

enum class ClientKind { BUYER, SELLER, TENANT, LANDLORD, OTHER }

enum class ListingPurpose { SALE, RENT }

enum class PropertyType { APARTMENT, HOUSE, TOWNHOUSE, COMMERCIAL, LAND, OTHER }

enum class PropertyStatus { ACTIVE, RESERVED, SOLD, RENTED, ARCHIVED }

enum class DealStage {
    LEAD,
    SHOWING,
    NEGOTIATION,
    CONTRACT,
    WON,
    LOST,
    ;

    fun isOpen(): Boolean = this != WON && this != LOST

    fun next(): DealStage? = when (this) {
        LEAD -> SHOWING
        SHOWING -> NEGOTIATION
        NEGOTIATION -> CONTRACT
        CONTRACT -> WON
        WON, LOST -> null
    }
}

enum class ActivityKind { NOTE, CALL, MESSAGE, MEETING, VIEWING }

enum class TaskFilter { ALL, OPEN, DONE, TODAY, OVERDUE }

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

data class Client(
    val id: String,
    val fullName: String,
    val phone: String,
    val email: String,
    val company: String,
    val kind: ClientKind,
    val source: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class Property(
    val id: String,
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
    val deletedAt: Long? = null,
)

data class PropertyPhoto(
    val id: String,
    val propertyId: String,
    val fileName: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class Deal(
    val id: String,
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
    val deletedAt: Long? = null,
)

data class DealListItem(
    val deal: Deal,
    val clientName: String?,
    val propertyTitle: String?,
)

data class CrmTask(
    val id: String,
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
    val deletedAt: Long? = null,
)

data class ActivityEntry(
    val id: String,
    val kind: ActivityKind,
    val body: String,
    val clientId: String?,
    val dealId: String?,
    val propertyId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class StageCount(
    val stage: DealStage,
    val count: Int,
)

data class DashboardSummary(
    val clientCount: Int,
    val activeListings: Int,
    val openDeals: Int,
    val dueToday: Int,
    val overdue: Int,
    val stages: List<StageCount>,
    val upcoming: List<CrmTask>,
    val recentDeals: List<DealListItem>,
)
