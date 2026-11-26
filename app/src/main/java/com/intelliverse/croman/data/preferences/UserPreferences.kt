package com.intelliverse.croman.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.intelliverse.croman.domain.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("croman_settings")

class UserPreferences(context: Context) {
    private val data = context.applicationContext.store
    val languageTag: Flow<String> = data.data.map { AppLanguage.normalize(it[Keys.LANGUAGE]) }
    val currencyCode: Flow<String> = data.data.map { it[Keys.CURRENCY].orEmpty() }
    val workspaceId: Flow<String> = data.data.map { it[Keys.WORKSPACE].orEmpty() }
    val demoIds: Flow<Set<String>> = data.data.map { it[Keys.DEMO].orEmpty() }

    suspend fun setLanguage(tag: String) {
        data.edit { it[Keys.LANGUAGE] = AppLanguage.normalize(tag) }
    }

    suspend fun setCurrency(code: String) {
        data.edit { it[Keys.CURRENCY] = code }
    }

    suspend fun setWorkspace(id: String) {
        data.edit { it[Keys.WORKSPACE] = id }
    }

    suspend fun rememberDemo(workspaceId: String, recordIds: Set<String>) {
        data.edit { prefs ->
            val next = prefs[Keys.DEMO].orEmpty().toMutableSet()
            recordIds.forEach { next += com.intelliverse.croman.data.demo.DemoIds.key(workspaceId, it) }
            prefs[Keys.DEMO] = next
        }
    }

    suspend fun forgetDemo(workspaceId: String, recordIds: Set<String>) {
        data.edit { prefs ->
            val drop = recordIds.map { com.intelliverse.croman.data.demo.DemoIds.key(workspaceId, it) }.toSet()
            prefs[Keys.DEMO] = prefs[Keys.DEMO].orEmpty().filterNot { it in drop }.toSet()
        }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_tag")
        val CURRENCY = stringPreferencesKey("currency_code")
        val WORKSPACE = stringPreferencesKey("workspace_id")
        val DEMO = stringSetPreferencesKey("demo_record_ids")
    }
}
