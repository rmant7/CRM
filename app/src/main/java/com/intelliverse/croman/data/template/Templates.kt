package com.intelliverse.croman.data.template

import android.content.Context
import com.intelliverse.croman.domain.TemplateFile
import kotlinx.serialization.json.Json

object TemplateCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    val bundled = listOf("realtor", "tutor")

    fun load(context: Context, id: String): TemplateFile {
        val text = context.assets.open("templates/$id.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(TemplateFile.serializer(), text)
    }
}
