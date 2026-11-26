package com.intelliverse.croman.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.intelliverse.croman.R
import com.intelliverse.croman.domain.DateTimes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun dateTimeLabel(epoch: Long?): String {
    if (epoch == null) return stringResource(R.string.no_due)
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(appLocale())
    return formatter.format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))
}

@Suppress("unused")
private val keep = DateTimes.combine(0, 0, 0, ZoneId.of("UTC"))
