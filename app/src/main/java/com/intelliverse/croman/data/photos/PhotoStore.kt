package com.intelliverse.croman.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class PhotoStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "photos").apply { mkdirs() }
    private val resolver = context.applicationContext.contentResolver

    fun directory(): File = dir
    fun file(name: String): File = File(dir, File(name).name)
    fun absolutePath(name: String): String = file(name).absolutePath

    fun delete(name: String) {
        file(name).delete()
    }

    fun import(uri: Uri, fileName: String) {
        val dest = file(fileName)
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "unreadable" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        normalize(dest)
    }

    fun writeBitmap(fileName: String, bitmap: Bitmap) {
        FileOutputStream(file(fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
        }
    }

    private fun normalize(dest: File) {
        val orientation = runCatching {
            ExifInterface(dest.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val decoded = BitmapFactory.decodeFile(dest.absolutePath) ?: return
        val rotated = rotate(decoded, orientation)
        val scaled = scaleDown(rotated, 1600)
        FileOutputStream(dest).use { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        if (scaled !== rotated) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return src
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
