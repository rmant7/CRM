package com.intelliverse.croman.ui.entities

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.intelliverse.croman.R
import com.intelliverse.croman.domain.ConsultationCard
import com.intelliverse.croman.domain.FieldDef
import com.intelliverse.croman.domain.FieldType
import com.intelliverse.croman.domain.L10n
import com.intelliverse.croman.domain.OptionDef
import com.intelliverse.croman.domain.ReplyClass
import com.intelliverse.croman.ui.components.CrmDropdown
import com.intelliverse.croman.ui.components.LtrField
import com.intelliverse.croman.ui.components.PickerField
import com.intelliverse.croman.ui.components.l10n
import com.intelliverse.croman.ui.format.dateTimeLabel

@Composable
fun ConsultationSections(
    fields: List<FieldDef>,
    values: Map<String, String>,
    onChange: (String, String) -> Unit,
    onClear: (String) -> Unit,
    onPickDate: (String) -> Unit,
) {
    ConsultationCard.sections.forEach { (id, keys) ->
        val defs = keys.mapNotNull { key -> fields.firstOrNull { it.key == key } }
        if (defs.isEmpty()) return@forEach
        SectionBlock(sectionTitle(id), ConsultationCard.filled(values, keys)) {
            defs.forEach { field -> FieldInput(field, values[field.key].orEmpty(), onChange, onClear, onPickDate) }
        }
    }
}

@Composable
internal fun SectionBlock(title: L10n, filled: Boolean, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            l10n(title) + if (filled) " · " + stringResource(R.string.section_filled) else " · " + stringResource(R.string.section_empty),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp),
        )
        if (open) content()
    }
}

@Composable
private fun FieldInput(
    field: FieldDef,
    value: String,
    onChange: (String, String) -> Unit,
    onClear: (String) -> Unit,
    onPickDate: (String) -> Unit,
) {
    when (field.fieldType()) {
        FieldType.SELECT -> CrmDropdown(
            l10n(field.label),
            field.options.firstOrNull { it.value == value }?.let { l10n(it.label) }.orEmpty(),
            field.options,
            { l10n(it.label) },
        ) { onChange(field.key, it.value) }
        FieldType.NUMBER, FieldType.MONEY -> LtrField(value, { onChange(field.key, it.filter { ch -> ch.isDigit() }) }, l10n(field.label))
        FieldType.DATE -> PickerField(
            l10n(field.label),
            if (value.isBlank()) stringResource(R.string.no_due) else dateTimeLabel(value.toLongOrNull()),
            { onPickDate(field.key) },
            { onClear(field.key) },
        )
        else -> OutlinedTextField(value, { onChange(field.key, it) }, Modifier.fillMaxWidth().padding(bottom = 8.dp), label = { Text(l10n(field.label)) })
    }
}

@Composable
fun DoNotContactMark() {
    Text(stringResource(R.string.do_not_contact), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
}

private fun sectionTitle(id: String): L10n = when (id) {
    "who" -> L10n("Who and why", "Кто и зачем", "מי ולמה")
    "points" -> L10n("Point A and point B", "Точка А и точка Б", "נקודה א ונקודה ב")
    "history" -> L10n("What they already tried", "Что уже пробовали", "מה כבר ניסו")
    "decision" -> L10n("How they choose", "Как выбирают", "איך בוחרים")
    "offer" -> L10n("Offer", "Предложение", "הצעה")
    "next" -> L10n("Status and next step", "Статус и следующий шаг", "סטטוס והצעד הבא")
    else -> L10n("Funnel", "Воронка", "משפך")
}

fun replyStopsContact(code: String): Boolean = ReplyClass.isStop(code)

fun shareCsv(context: android.content.Context, name: String, bytes: ByteArray) {
    val dir = java.io.File(context.cacheDir, "export").apply { mkdirs() }
    val file = java.io.File(dir, name)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, name))
}

@Composable
fun OutreachReply(value: String, onChange: (String) -> Unit) {
    val options = ReplyClass.codes.map { code ->
        com.intelliverse.croman.domain.OptionDef(code, ReplyClass.label(code).let { L10n("${code} ${it.en}", "${code} ${it.ru}", "${code} ${it.he}") })
    }
    CrmDropdown(
        stringResource(R.string.reply_class),
        options.firstOrNull { it.value == value }?.let { l10n(it.label) }.orEmpty(),
        options,
        { l10n(it.label) },
    ) { onChange(it.value) }
    if (ReplyClass.isStop(value)) DoNotContactMark()
}
