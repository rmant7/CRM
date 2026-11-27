package com.intelliverse.croman.domain

/** Fixed reply classifier for outreach. X1 means stop contacting. */
object ReplyClass {
    const val STOP = "X1"

    val codes = listOf(
        "P1", "P2", "P3", "P4",
        "N1", "N2", "N3", "N4", "N5", "N6", "N7", "N8", "N9",
        "U1", "U2", "U3",
        "X1",
    )

    fun label(code: String): L10n = when (code) {
        "P1" -> L10n("Ready to take part", "Готов участвовать", "מוכן להשתתף")
        "P2" -> L10n("Wants details", "Хочет подробности", "רוצה פרטים")
        "P3" -> L10n("Wants material", "Хочет материал", "רוצה חומר")
        "P4" -> L10n("Discussion", "Обсуждение", "דיון")
        "N1" -> L10n("Not relevant", "Не актуально", "לא רלוונטי")
        "N2" -> L10n("No time", "Нет времени", "אין זמן")
        "N3" -> L10n("Unclear", "Непонятно", "לא ברור")
        "N4" -> L10n("Trust", "Доверие", "אמון")
        "N5" -> L10n("Topic", "Тема", "נושא")
        "N6" -> L10n("Format", "Формат", "פורמט")
        "N7" -> L10n("Commercial", "Коммерция", "מסחרי")
        "N8" -> L10n("Later", "Позже", "מאוחר יותר")
        "N9" -> L10n("Other no", "Другой отказ", "סירוב אחר")
        "U1" -> L10n("Neutral", "Нейтрально", "ניטרלי")
        "U2" -> L10n("Autoreply", "Автоответ", "מענה אוטומטי")
        "U3" -> L10n("Unknown", "Неизвестно", "לא ידוע")
        "X1" -> L10n("Stop contacting", "Больше не писать", "להפסיק לפנות")
        else -> L10n(code, code, code)
    }

    fun isStop(code: String?): Boolean = code == STOP
}

object ConsultationCard {
    const val TYPE = "consultation"
    const val NEXT_STEP_AT = "next_step_at"
    const val REPLY = "reply_class"

    val sections = listOf(
        "who" to listOf("meeting_date", "source", "contact_type", "meeting_reason"),
        "points" to listOf("point_a", "urgency", "point_b", "result_criteria"),
        "history" to listOf("tried", "tried_result", "blocked"),
        "decision" to listOf("choice_criteria", "agreed_summary", "impressions"),
        "offer" to listOf("proposed_format", "price", "doubts", "refusal_words"),
        "next" to listOf("status", "next_step", NEXT_STEP_AT),
        "funnel" to listOf("meeting_type", "has_request", "offer_made", "price_reaction", "paid", "paid_amount", "paid_results"),
    )

    fun filled(fields: Map<String, String>, keys: List<String>): Boolean =
        keys.any { fields[it].orEmpty().isNotBlank() }
}

object CsvExport {
    fun document(headers: List<String>, rows: List<List<String>>): ByteArray {
        val body = buildString {
            append(headers.joinToString(",") { escape(it) })
            rows.forEach { row ->
                append('\n')
                append(row.joinToString(",") { escape(it) })
            }
        }
        return ("\uFEFF$body").toByteArray(Charsets.UTF_8)
    }

    fun escape(value: String): String {
        val quote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val cleaned = value.replace("\"", "\"\"")
        return if (quote) "\"$cleaned\"" else cleaned
    }
}
