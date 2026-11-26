package com.intelliverse.croman

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal object DeviceShots {
    fun save(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots",
        )
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
