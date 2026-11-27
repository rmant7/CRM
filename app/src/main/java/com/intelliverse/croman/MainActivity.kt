package com.intelliverse.croman

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.intelliverse.croman.data.repo.seedVolume
import com.intelliverse.croman.data.template.TemplateCatalog
import com.intelliverse.croman.di.LocalAppContainer
import com.intelliverse.croman.ui.CromanRoot
import com.intelliverse.croman.ui.theme.CromanTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var openTaskId by mutableStateOf<String?>(null)
    private var incoming by mutableStateOf<com.intelliverse.croman.ui.share.IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        incoming = com.intelliverse.croman.ui.share.ShareIntents.from(intent, contentResolver, referrer)
        val container = (application as CromanApp).container
        if (intent?.getBooleanExtra(EXTRA_BENCHMARK, false) == true) {
            CoroutineScope(Dispatchers.IO).launch {
                val workspaceId = container.settings.workspaceId.first().ifBlank {
                    container.store.observeWorkspaces().first().firstOrNull()?.id
                } ?: return@launch
                val template = TemplateCatalog.load(this@MainActivity, "realtor")
                val type = template.entityTypes.first { it.key == "property" }
                container.store.seedVolume(workspaceId, type, 3000)
                val (count, ms) = container.store.benchmarkSearch(workspaceId, "property", "tel")
                Log.i(BENCH_TAG, "hits=$count ms=$ms")
            }
        }
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                CromanTheme {
                    Surface(Modifier.fillMaxSize()) { CromanRoot(openTaskId, incoming) { incoming = null } }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTaskId = intent.getStringExtra(EXTRA_TASK_ID)
        incoming = com.intelliverse.croman.ui.share.ShareIntents.from(intent, contentResolver, referrer)
    }

    /** Used by the device test. `onNewIntent` is protected. */
    fun acceptIncoming(intent: Intent) {
        onNewIntent(intent)
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_BENCHMARK = "benchmark"
        const val BENCH_TAG = "CROMAN_BENCH"
    }
}
