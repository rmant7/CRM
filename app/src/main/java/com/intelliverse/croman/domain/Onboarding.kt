package com.intelliverse.croman.domain

/**
 * First-launch choices. A template is offered only when it already has people,
 * a real piece of work (visits, deals, requests, or a pipeline), and a menu
 * that can open both. The generic "universal" file is not a working CRM.
 */
data class OnboardingOffer(
    val templateId: String,
    val phrase: L10n,
    val keywords: List<String>,
)

data class OnboardingMatch(val offer: OnboardingOffer, val recognized: Boolean)

object Onboarding {
    private val catalog = listOf(
        OnboardingOffer(
            "realtor",
            L10n("I sell and rent property", "Продаю и сдаю недвижимость", "מוכר ומשכיר נדל\"ן"),
            listOf("property", "realtor", "apartment", "flat", "rent", "listing", "недвиж", "квартир", "аренд", "риелтор", "נדל", "דיר", "השכר"),
        ),
        OnboardingOffer(
            "tutor",
            L10n("I give lessons", "Даю уроки", "נותן שיעורים"),
            listOf("lesson", "tutor", "teach", "student", "урок", "репетитор", "препода", "ученик", "שיעור", "מורה", "תלמיד"),
        ),
        OnboardingOffer(
            "beauty",
            L10n("I do beauty treatments", "Делаю красоту", "עושה טיפולי יופי"),
            listOf("beauty", "salon", "nails", "hair", "красот", "салон", "маник", "יופי", "מספר"),
        ),
        OnboardingOffer(
            "health",
            L10n("I see patients", "Принимаю пациентов", "מקבל מטופלים"),
            listOf("patient", "clinic", "doctor", "health", "пациент", "клиник", "врач", "здоров", "מטופל", "קליניק", "רופא"),
        ),
        OnboardingOffer(
            "sport",
            L10n("I train people", "Тренирую людей", "מאמן אנשים"),
            listOf("train", "coach", "fitness", "gym", "трени", "фитнес", "спорт", "כושר", "מאמן"),
        ),
        OnboardingOffer(
            "repair",
            L10n("I repair homes", "Ремонтирую дома", "מתקן בתים"),
            listOf("repair", "plumber", "renovation", "ремонт", "сантех", "שיפוץ", "אינסטל"),
        ),
        OnboardingOffer(
            "car",
            L10n("I service cars", "Обслуживаю автомобили", "מטפל ברכבים"),
            listOf("car", "auto", "garage", "detail", "авто", "машин", "детейл", "רכב", "מוסך"),
        ),
        OnboardingOffer(
            "tourism",
            L10n("I organize trips and events", "Организую поездки и события", "מארגן טיולים ואירועים"),
            listOf("tour", "trip", "event", "travel", "тур", "поезд", "событ", "טיול", "אירוע"),
        ),
        OnboardingOffer(
            "professional",
            L10n("I run projects for clients", "Веду проекты для клиентов", "מוביל פרויקטים ללקוחות"),
            listOf("project", "client work", "consult", "проект", "заказ", "פרויקט"),
        ),
    )

    fun isReady(template: TemplateFile): Boolean {
        if (template.id == "universal" || template.contactProfile == null) return false
        if (template.menu.none { it.route == "contacts" }) return false
        if (template.menu.none { it.route.startsWith("entities:") }) return false
        return template.entityTypes.any { type ->
            type.behaviors.any { it != "visit_reminder" } ||
                (type.pipeline?.stages?.size ?: 0) >= 3 ||
                type.fields.size >= 5
        }
    }

    val primaryIds = listOf("realtor", "tutor")

    fun offers(readyTemplateIds: Set<String>): List<OnboardingOffer> =
        catalog.filter { it.templateId in readyTemplateIds }

    fun primary(offers: List<OnboardingOffer>): List<OnboardingOffer> =
        offers.filter { it.templateId in primaryIds }

    fun moreFields(offers: List<OnboardingOffer>): List<OnboardingOffer> =
        offers.filter { it.templateId !in primaryIds }

    fun match(description: String, offers: List<OnboardingOffer>): OnboardingMatch {
        require(offers.isNotEmpty())
        val folded = TextFold.fold(description)
        val scored = offers.map { offer ->
            offer to offer.keywords.count { key ->
                val needle = TextFold.fold(key)
                needle.isNotBlank() && folded.contains(needle)
            }
        }
        val best = scored.maxOf { it.second }
        val chosen = if (best == 0) {
            offers.firstOrNull { it.templateId == "professional" } ?: offers.first()
        } else {
            scored.first { it.second == best }.first
        }
        return OnboardingMatch(chosen, best > 0)
    }

    fun homeLine(templateId: String): L10n = when (templateId) {
        "realtor" -> L10n("Clients, listings, and deals.", "Клиенты, объекты и сделки.", "לקוחות, נכסים ועסקאות.")
        "tutor" -> L10n("Students, lessons, and packages.", "Ученики, уроки и пакеты.", "תלמידים, שיעורים וחבילות.")
        "beauty" -> L10n("Clients, visits, and services.", "Клиенты, визиты и услуги.", "לקוחות, ביקורים ושירותים.")
        "health" -> L10n("Patients and visits.", "Пациенты и визиты.", "מטופלים וביקורים.")
        "sport" -> L10n("People you train and their visits.", "Кого тренируете и их визиты.", "המתאמנים והביקורים שלהם.")
        "repair" -> L10n("Clients and repair requests.", "Клиенты и заявки на ремонт.", "לקוחות וקריאות תיקון.")
        "car" -> L10n("Clients and car jobs.", "Клиенты и работы по авто.", "לקוחות ועבודות רכב.")
        "tourism" -> L10n("Clients, trips, and events.", "Клиенты, поездки и события.", "לקוחות, טיולים ואירועים.")
        "professional" -> L10n("Clients and projects.", "Клиенты и проекты.", "לקוחות ופרויקטים.")
        else -> L10n("People and the work you do with them.", "Люди и работа с ними.", "אנשים והעבודה איתם.")
    }
}

enum class HomePrompt { EMPTY, DEMO_ONLY, HAS_REAL }

fun homePrompt(realClients: Int, demoPresent: Boolean): HomePrompt = when {
    realClients > 0 -> HomePrompt.HAS_REAL
    demoPresent -> HomePrompt.DEMO_ONLY
    else -> HomePrompt.EMPTY
}
