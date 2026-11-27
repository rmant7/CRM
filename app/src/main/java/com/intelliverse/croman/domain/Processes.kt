package com.intelliverse.croman.domain

/** Concrete process engines. A workspace picks one as primary and may enable more later. */
enum class ProcessKey(val wire: String) {
    APPOINTMENT("appointment"),
    OBJECT_DEAL("object_deal"),
    FIELD_SERVICE("field_service"),
    PROJECT_ORDER("project_order"),
    ;

    companion object {
        fun fromWire(value: String): ProcessKey? = entries.firstOrNull { it.wire == value }
    }
}

interface ProcessEngine {
    val key: ProcessKey
    val contributionAsset: String
}

object AppointmentEngine : ProcessEngine {
    override val key = ProcessKey.APPOINTMENT
    override val contributionAsset = "process_appointment"
    const val PACKAGE = "package_deduction"
    const val SERIES = "weekly_series"
    const val REMINDER = "visit_reminder"

    fun usesSharedSchedule(type: EntityTypeDef): Boolean =
        PACKAGE in type.behaviors || REMINDER in type.behaviors || SERIES in type.behaviors
}

object ObjectDealEngine : ProcessEngine {
    override val key = ProcessKey.OBJECT_DEAL
    override val contributionAsset = "process_object"
}

object FieldServiceEngine : ProcessEngine {
    override val key = ProcessKey.FIELD_SERVICE
    override val contributionAsset = "process_field"
}

object ProjectOrderEngine : ProcessEngine {
    override val key = ProcessKey.PROJECT_ORDER
    override val contributionAsset = "process_project"
}

object ProcessEngines {
    val all: List<ProcessEngine> = listOf(AppointmentEngine, ObjectDealEngine, FieldServiceEngine, ProjectOrderEngine)

    fun get(wire: String): ProcessEngine? = all.firstOrNull { it.key.wire == wire }

    fun label(key: ProcessKey): L10n = when (key) {
        ProcessKey.APPOINTMENT -> L10n("Appointments", "Записи", "תורים")
        ProcessKey.OBJECT_DEAL -> L10n("Objects and deals", "Объекты и сделки", "נכסים ועסקאות")
        ProcessKey.FIELD_SERVICE -> L10n("Field requests", "Заявки", "קריאות שטח")
        ProcessKey.PROJECT_ORDER -> L10n("Projects and orders", "Проекты и заказы", "פרויקטים והזמנות")
    }
}

data class CategorySpec(
    val id: String,
    val templateId: String,
    val process: ProcessKey,
    val name: L10n,
)

object CategoryCatalog {
    val all: List<CategorySpec> = listOf(
        CategorySpec("real_estate", "realtor", ProcessKey.OBJECT_DEAL, L10n("Real estate", "Недвижимость", "נדל\"ן")),
        CategorySpec("tutoring", "tutor", ProcessKey.APPOINTMENT, L10n("Tutoring and education", "Репетиторство и обучение", "הוראה וחינוך")),
        CategorySpec("beauty", "beauty", ProcessKey.APPOINTMENT, L10n("Beauty salons", "Салоны красоты", "מכוני יופי")),
        CategorySpec("health", "health", ProcessKey.APPOINTMENT, L10n("Health and private practice", "Здоровье и частная практика", "בריאות וקליניקה")),
        CategorySpec("sport", "sport", ProcessKey.APPOINTMENT, L10n("Sport and fitness", "Спорт и фитнес", "ספורט וכושר")),
        CategorySpec("pets", "pets", ProcessKey.APPOINTMENT, L10n("Pet services", "Услуги для животных", "שירותי חיות")),
        CategorySpec("repair", "repair", ProcessKey.FIELD_SERVICE, L10n("Home repair and maintenance", "Ремонт и обслуживание дома", "שיפוץ ותחזוקת הבית")),
        CategorySpec("car", "car", ProcessKey.FIELD_SERVICE, L10n("Car service and detailing", "Автосервис и детейлинг", "מוסך ודיטיילינג")),
        CategorySpec("tourism", "tourism", ProcessKey.PROJECT_ORDER, L10n("Tourism and events", "Туризм и события", "תיירות ואירועים")),
        CategorySpec("professional", "professional", ProcessKey.PROJECT_ORDER, L10n("Professional and IT services", "Профессиональные и IT-услуги", "שירותים מקצועיים ו-IT")),
        CategorySpec("consultation", "consultation", ProcessKey.APPOINTMENT, L10n("Consultations", "Консультации", "ייעוץ")),
        CategorySpec("other", "universal", ProcessKey.PROJECT_ORDER, L10n("Other", "Другое", "אחר")),
    )

    fun byId(id: String): CategorySpec? = all.firstOrNull { it.id == id }

    fun byTemplate(templateId: String): CategorySpec? = all.firstOrNull { it.templateId == templateId }

    fun merge(base: TemplateFile, enabledProcesses: String, extras: Map<String, TemplateFile>): TemplateFile {
        val primary = base.process.ifBlank { byTemplate(base.id)?.process?.wire.orEmpty() }
        val extraKeys = enabledProcesses.split(',').map { it.trim() }.filter { it.isNotBlank() && it != primary }
        var menu = base.menu
        var types = base.entityTypes
        extraKeys.forEach { key ->
            val extra = extras[key] ?: return@forEach
            menu = (menu + extra.menu.filter { it.route != "home" }).distinctBy { it.route }
            types = (types + extra.entityTypes).distinctBy { it.key }
        }
        return base.copy(menu = menu, entityTypes = types)
    }
}
