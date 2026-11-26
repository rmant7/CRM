package com.intelliverse.croman.ui.settings

import androidx.lifecycle.ViewModel
import com.intelliverse.croman.domain.WorkspaceRules

class WorkspaceEditorViewModel : ViewModel() {
    fun validate(existingNames: List<String>, requested: String, templateDefault: String): Result<String> =
        runCatching { WorkspaceRules.validateNewName(existingNames, requested, templateDefault) }
}
