package com.intelliverse.croman.data.template

import android.content.Context
import com.intelliverse.croman.domain.CategoryCatalog
import com.intelliverse.croman.domain.ProcessEngines
import com.intelliverse.croman.domain.TemplateFile
import com.intelliverse.croman.domain.Workspace
import kotlinx.serialization.json.Json

object TemplateCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    val bundled = CategoryCatalog.all.map { it.templateId }

    fun load(context: Context, id: String): TemplateFile {
        val text = context.assets.open("templates/$id.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(TemplateFile.serializer(), text)
    }

    fun effective(context: Context, workspace: Workspace): TemplateFile {
        val base = load(context, workspace.templateId)
        val extras = ProcessEngines.all.associate { engine ->
            engine.key.wire to runCatching { load(context, engine.contributionAsset) }.getOrNull()
        }.filterValues { it != null }.mapValues { it.value!! }
        return CategoryCatalog.merge(base, workspace.enabledProcesses, extras)
    }
}
