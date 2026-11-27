package com.intelliverse.croman

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal object DeviceShots {
    fun save(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(600)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots",
        )
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
