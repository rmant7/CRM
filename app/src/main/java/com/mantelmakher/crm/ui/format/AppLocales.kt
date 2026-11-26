package com.mantelmakher.crm.ui.format

import androidx.core.os.LocaleListCompat
import com.mantelmakher.crm.domain.logic.AppLanguage

/**
 * Per-app locales. Android 13+ also reads res/xml/locales_config.xml.
 * AppCompatDelegate applies the same choice on older releases and can persist it.
 */
object AppLocales {
    fun list(tag: String): LocaleListCompat {
        val normalized = AppLanguage.normalize(tag)
        if (normalized == AppLanguage.SYSTEM) return LocaleListCompat.getEmptyLocaleList()
        return LocaleListCompat.forLanguageTags(normalized)
    }
}
