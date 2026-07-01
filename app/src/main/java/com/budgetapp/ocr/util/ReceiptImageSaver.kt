package com.budgetapp.ocr.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

object ReceiptImageSaver {
    private const val DIR = "receipts"
    private const val QUALITY = 75

    fun save(context: Context, bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.isRecycled) return null
        val dir = File(context.filesDir, DIR).also { it.mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.webp")
        return try {
            FileOutputStream(file).use { out ->
                val format = if (Build.VERSION.SDK_INT >= 30)
                    Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                bitmap.compress(format, QUALITY, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.w("ReceiptImageSaver", "Save failed: ${e.message}")
            null
        }
    }

    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return File(path).delete()
    }
}
