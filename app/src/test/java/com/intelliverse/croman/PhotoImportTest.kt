package com.intelliverse.croman

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.intelliverse.croman.data.photos.PhotoStore
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoImportTest {
    @Test
    fun importDownscalesAWidePhoto() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "wide.jpg")
        val bitmap = Bitmap.createBitmap(2400, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        FileOutputStream(source).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val photos = PhotoStore(context)
        photos.import(Uri.fromFile(source), "imported.jpg")
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(photos.absolutePath("imported.jpg"), bounds)
        assertTrue("width ${bounds.outWidth}", bounds.outWidth in 1..1600)
        assertTrue("height ${bounds.outHeight}", bounds.outHeight in 1..1600)
        assertTrue(bounds.outWidth < 2400)
    }
}
