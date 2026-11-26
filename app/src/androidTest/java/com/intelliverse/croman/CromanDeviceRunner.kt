package com.intelliverse.croman

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.intelliverse.croman.domain.AppLanguage
import com.intelliverse.croman.ui.format.AppLocales

/** Applies the `locale` instrumentation argument before the app starts. */
class CromanDeviceRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        localeTag(arguments)?.let { tag ->
            AppCompatDelegate.setApplicationLocales(AppLocales.list(tag))
        }
    }

    override fun callApplicationOnCreate(app: Application) {
        val raw = localeTag(null)
        if (app is CromanApp && raw != null) app.deviceTestLocale = raw
        super.callApplicationOnCreate(app)
    }

    private fun localeTag(arguments: Bundle?): String? {
        val raw = arguments?.getString("locale")
            ?: runCatching { InstrumentationRegistry.getArguments().getString("locale") }.getOrNull()
            ?: bundleLocale()
        if (raw.isNullOrBlank()) return null
        return AppLanguage.normalize(raw)
    }

    private fun bundleLocale(): String? {
        val method = generateSequence<Class<*>>(javaClass) { it.superclass }
            .firstNotNullOfOrNull { clazz ->
                runCatching { clazz.getDeclaredField("arguments") }.getOrNull()
            } ?: return null
        method.isAccessible = true
        val bundle = method.get(this) as? Bundle ?: return null
        return bundle.getString("locale")
    }
}
