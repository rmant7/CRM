package com.mantelmakher.crm

import android.content.Intent
import android.os.Bundle
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
import com.mantelmakher.crm.di.LocalAppContainer
import com.mantelmakher.crm.ui.CrmRoot
import com.mantelmakher.crm.ui.theme.MantelCrmTheme

class MainActivity : AppCompatActivity() {
    private var openTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val container = (application as CrmApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                MantelCrmTheme {
                    Surface(Modifier.fillMaxSize()) {
                        CrmRoot(initialTaskId = openTaskId)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTaskId = intent.getStringExtra(EXTRA_TASK_ID)
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
    }
}
