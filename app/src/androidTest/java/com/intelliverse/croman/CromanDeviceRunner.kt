package com.intelliverse.croman

import android.app.Application
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.intelliverse.croman.domain.AppLanguage

/** Applies the `locale` instrumentation argument before the app starts. */
class CromanDeviceRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        val raw = runCatching { InstrumentationRegistry.getArguments().getString("locale") }.getOrNull()
            ?: bundleLocale()
        if (app is CromanApp && !raw.isNullOrBlank()) {
            app.deviceTestLocale = AppLanguage.normalize(raw)
        }
        super.callApplicationOnCreate(app)
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
