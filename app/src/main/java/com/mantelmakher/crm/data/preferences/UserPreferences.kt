package com.mantelmakher.crm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mantelmakher.crm.domain.logic.AppLanguage
import com.mantelmakher.crm.domain.repository.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "mantel_crm_settings")

class UserPreferences(context: Context) : UserSettings {
    private val store = context.applicationContext.settingsDataStore

    override val languageTag: Flow<String> = store.data.map { prefs ->
        AppLanguage.normalize(prefs[Keys.LANGUAGE])
    }

    override val currencyCode: Flow<String> = store.data.map { prefs ->
        prefs[Keys.CURRENCY].orEmpty()
    }

    override suspend fun setLanguage(tag: String) {
        store.edit { prefs -> prefs[Keys.LANGUAGE] = AppLanguage.normalize(tag) }
    }

    override suspend fun setCurrency(code: String) {
        store.edit { prefs -> prefs[Keys.CURRENCY] = code }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_tag")
        val CURRENCY = stringPreferencesKey("currency_code")
    }
}
