package com.intelliverse.croman.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    suspend fun setLanguage(tag: String) {
        data.edit { it[Keys.LANGUAGE] = AppLanguage.normalize(tag) }
    }

    suspend fun setCurrency(code: String) {
        data.edit { it[Keys.CURRENCY] = code }
    }

    suspend fun setWorkspace(id: String) {
        data.edit { it[Keys.WORKSPACE] = id }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_tag")
        val CURRENCY = stringPreferencesKey("currency_code")
        val WORKSPACE = stringPreferencesKey("workspace_id")
    }
}
