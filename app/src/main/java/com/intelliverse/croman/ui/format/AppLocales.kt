package com.intelliverse.croman.ui.format

import androidx.core.os.LocaleListCompat
import com.intelliverse.croman.domain.AppLanguage

object AppLocales {
    fun list(tag: String): LocaleListCompat {
        val normalized = AppLanguage.normalize(tag)
        if (normalized == AppLanguage.SYSTEM) return LocaleListCompat.getEmptyLocaleList()
        return LocaleListCompat.forLanguageTags(normalized)
    }
}
