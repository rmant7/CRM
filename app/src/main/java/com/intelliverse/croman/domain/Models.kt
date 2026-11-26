package com.intelliverse.croman.domain

import java.util.UUID
import kotlinx.serialization.Serializable

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

object LocalUser {
    const val ID = "00000000-0000-4000-8000-000000000001"
}

enum class FieldType { TEXT, NUMBER, DATE, MONEY, BOOLEAN, SELECT, MULTI, REFERENCE, FILE }

enum class MessageStatus { PREPARED, OPENED, SENT }

enum class PaymentKind { INCOME, EXPENSE, DEBT }

enum class ContactKind { BUYER, SELLER, TENANT, LANDLORD, OTHER }

enum class TaskFilter { ALL, OPEN, DONE, TODAY, OVERDUE }

@Serializable
data class L10n(val en: String, val ru: String, val he: String) {
    fun text(language: String): String = when (language.lowercase()) {
        "ru" -> ru
        "he", "iw" -> he
        else -> en
    }
}

@Serializable
data class OptionDef(val value: String, val label: L10n)

@Serializable
data class FieldDef(
    val key: String,
    val label: L10n,
    val type: String,
    val required: Boolean = false,
    val options: List<OptionDef> = emptyList(),
    val referenceType: String? = null,
) {
    fun fieldType(): FieldType = when (type) {
        "number" -> FieldType.NUMBER
        "date" -> FieldType.DATE
        "money" -> FieldType.MONEY
        "boolean" -> FieldType.BOOLEAN
        "select" -> FieldType.SELECT
        "multi" -> FieldType.MULTI
        "reference" -> FieldType.REFERENCE
        "file" -> FieldType.FILE
        else -> FieldType.TEXT
    }
}

@Serializable
data class StageDef(
    val key: String,
    val label: L10n,
    val won: Boolean = false,
    val lost: Boolean = false,
) {
    fun isOpen(): Boolean = !won && !lost
}

@Serializable
data class PipelineDef(val key: String, val stages: List<StageDef>)

@Serializable
data class RelationDef(val key: String, val label: L10n, val target: String)

@Serializable
data class EntityTypeDef(
    val key: String,
    val label: L10n,
    val hasPhotos: Boolean = false,
    val pipeline: PipelineDef? = null,
    val fields: List<FieldDef> = emptyList(),
    val relations: List<RelationDef> = emptyList(),
    val listBadge: String? = null,
    val listMoney: String? = null,
    val listSubtitle: List<String> = emptyList(),
    val emptyTitle: L10n? = null,
    val emptyBody: L10n? = null,
    val behaviors: List<String> = emptyList(),
)

@Serializable
data class MenuItem(val route: String, val label: L10n)

@Serializable
data class MessageTemplate(val key: String, val label: L10n, val body: L10n)

@Serializable
data class RenameDef(val type: String, val from: String, val to: String)

@Serializable
data class SplitDef(val type: String, val from: String, val into: List<String>, val delimiter: String)

@Serializable
data class MigrationDef(
    val from: Int,
    val to: Int,
    val renames: List<RenameDef> = emptyList(),
    val splits: List<SplitDef> = emptyList(),
)

@Serializable
data class TemplateFile(
    val id: String,
    val version: Int,
    val name: L10n,
    val country: String,
    val dashboard: String,
    val messengers: List<String> = emptyList(),
    val messageTemplates: List<MessageTemplate> = emptyList(),
    val menu: List<MenuItem> = emptyList(),
    val entityTypes: List<EntityTypeDef> = emptyList(),
    val migrations: List<MigrationDef> = emptyList(),
    val features: List<String> = emptyList(),
    val contactProfile: ContactProfile? = null,
    val category: String = "",
    val process: String = "",
)

@Serializable
data class ContactProfile(
    val title: L10n,
    val emptyTitle: L10n,
    val emptyBody: L10n,
    val kinds: List<OptionDef> = emptyList(),
    val fields: List<FieldDef> = emptyList(),
)

data class Workspace(
    val id: String,
    val name: String,
    val templateId: String,
    val templateVersion: Int,
    val country: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val category: String = "",
    val primaryProcess: String = "",
    val enabledProcesses: String = "",
)

data class Contact(
    val id: String,
    val workspaceId: String,
    val fullName: String,
    val phone: String,
    val email: String,
    val company: String,
    val kind: ContactKind,
    val source: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class EntityRow(
    val id: String,
    val workspaceId: String,
    val typeKey: String,
    val title: String,
    val stageKey: String?,
    val subtitle: String,
    val badge: String,
    val moneyAmount: Long,
    val moneyCurrency: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class TaskItem(
    val id: String,
    val workspaceId: String,
    val title: String,
    val details: String,
    val dueAt: Long?,
    val reminderAt: Long?,
    val completedAt: Long?,
    val contactId: String?,
    val entityId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class ActivityEntry(
    val id: String,
    val workspaceId: String,
    val kind: String,
    val body: String,
    val contactId: String?,
    val entityId: String?,
    val createdAt: Long,
)

data class DocumentFile(
    val id: String,
    val workspaceId: String,
    val ownerId: String,
    val fileName: String,
    val mime: String,
    val title: String,
    val sortOrder: Int,
)

data class PaymentRecord(
    val id: String,
    val workspaceId: String,
    val kind: PaymentKind,
    val amount: Long,
    val currency: String,
    val occurredAt: Long,
    val contactId: String?,
    val entityId: String?,
    val note: String,
)

data class CatalogItem(
    val id: String,
    val workspaceId: String,
    val name: String,
    val price: Long,
    val currency: String,
    val unit: String,
)

data class CalendarEvent(
    val id: String,
    val workspaceId: String,
    val title: String,
    val startsAt: Long,
    val endsAt: Long?,
    val contactId: String?,
    val entityId: String?,
)

data class MessageEntry(
    val id: String,
    val workspaceId: String,
    val contactId: String,
    val messengerId: String,
    val body: String,
    val status: MessageStatus,
    val createdAt: Long,
)

data class StageCount(val key: String, val label: L10n, val count: Int)

data class HomeSummary(
    val contacts: Int,
    val activeListings: Int,
    val openDeals: Int,
    val dueToday: Int,
    val overdue: Int,
    val stages: List<StageCount>,
    val upcoming: List<TaskItem>,
    val recentDeals: List<EntityRow>,
)

data class Change(
    val id: String,
    val workspaceId: String,
    val tableName: String,
    val subjectId: String,
    val operation: String,
    val changedAt: Long,
)
