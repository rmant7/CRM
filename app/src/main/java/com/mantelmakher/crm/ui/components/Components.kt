package com.mantelmakher.crm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mantelmakher.crm.R
import com.mantelmakher.crm.domain.logic.DateTimes
import com.mantelmakher.crm.domain.logic.Initials
import com.mantelmakher.crm.domain.model.ActivityEntry
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.ui.format.dateTimeLabel
import com.mantelmakher.crm.ui.format.labelRes
import java.time.ZoneId

private val AvatarColors = listOf(
    Color(0xFF0E6B57),
    Color(0xFF1E4D78),
    Color(0xFF8A4B2F),
    Color(0xFF5C4B8A),
    Color(0xFF8A3D55),
    Color(0xFF2F6F62),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        content = content,
    )
}

@Composable
fun AddFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add))
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search)) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
    )
}

@Composable
fun <T> ChipRow(
    options: List<T?>,
    selected: T?,
    label: @Composable (T?) -> String,
    onSelect: (T?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it?.toString() ?: "all" }) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun NoteComposer(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.note_hint)) },
            minLines = 2,
        )
        TextButton(
            onClick = {
                val body = text.trim()
                if (body.isNotEmpty()) {
                    onSubmit(body)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) { Text(stringResource(R.string.add_note)) }
    }
}

@Composable
fun HistoryBlock(entries: List<ActivityEntry>) {
    if (entries.isEmpty()) {
        Text(
            stringResource(R.string.empty_notes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        entries.forEach { entry ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(stringResource(entry.kind.labelRes()), style = MaterialTheme.typography.labelLarge)
                Text(entry.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    dateTimeLabel(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val color = AvatarColors[name.hashCode().mod(AvatarColors.size)]
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(Initials.of(name), color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun StageBadge(stage: DealStage) {
    val (background, foreground) = when (stage) {
        DealStage.WON -> Color(0xFF0E6B57) to Color.White
        DealStage.LOST -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        DealStage.CONTRACT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DealStage.NEGOTIATION -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Badge(stringResource(stage.labelRes()), background, foreground)
}

@Composable
fun StatusBadge(status: PropertyStatus) {
    val (background, foreground) = when (status) {
        PropertyStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PropertyStatus.RESERVED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        PropertyStatus.SOLD, PropertyStatus.RENTED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PropertyStatus.ARCHIVED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Badge(stringResource(status.labelRes()), background, foreground)
}

@Composable
fun Badge(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun ConfirmDeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete_title)) },
        text = { Text(stringResource(R.string.confirm_delete_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.keep)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CrmDropdown(
    label: String,
    valueLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = valueLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun LtrTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { message -> { Text(message) } },
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(textDirection = TextDirection.Ltr),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDateTimePicker(onPicked: (Long) -> Unit): () -> Unit {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = state.selectedDateMillis ?: return@TextButton
                        dateMillis = selected
                        showDate = false
                        showTime = true
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = 10, initialMinute = 0)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.select_time), style = MaterialTheme.typography.titleMedium)
                    TimePicker(state = state)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(
                            onClick = {
                                val date = dateMillis ?: return@TextButton
                                onPicked(DateTimes.combine(date, state.hour, state.minute, ZoneId.systemDefault()))
                                showTime = false
                            },
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
    return { showDate = true }
}

@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = if (onClear != null) {
                {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            } else {
                null
            },
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (onClear != null) 48.dp else 0.dp)
                .clickable(onClick = onClick),
        )
    }
}
