package com.budgetapp.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import kotlin.math.sqrt

/**
 * Class for processing document images (receipts, invoices, etc.)
 * Improves contrast, aligns perspective, and detects edges
 */
class DocumentScanner {

    /**
     * Image preprocessing - improves contrast and sharpness
     */
    fun preprocessImage(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to grayscale and equalize histogram (CLAHE)
        val grayPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            grayPixels[i] = gray
        }

        // Equalize contrast (simple CLAHE)
        val enhanced = enhanceContrast(grayPixels, width, height)

        // Convert back to color
        val result = IntArray(width * height)
        for (i in enhanced.indices) {
            val gray = enhanced[i]
            result[i] = Color.rgb(gray, gray, gray)
        }

        return Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Contrast enhancement - increases the difference between light and dark
     */
    private fun enhanceContrast(pixels: IntArray, width: Int, height: Int): IntArray {
        // Calculate min and max
        var minVal = 255
        var maxVal = 0
        for (pixel in pixels) {
            if (pixel < minVal) minVal = pixel
            if (pixel > maxVal) maxVal = pixel
        }

        val range = maxVal - minVal
        if (range == 0) return pixels

        // Stretch contrast
        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val normalized = ((pixels[i] - minVal) * 255) / range
            result[i] = normalized.coerceIn(0, 255)
        }

        return result
    }

    /**
     * Detect document edges (simple method)
     * @return List of 4 edge points (top-left, top-right, bottom-right, bottom-left)
     */
    fun detectDocumentEdges(bitmap: Bitmap): List<Point> {
        val width = bitmap.width
        val height = bitmap.height

        // Simple approach: search for edges (transitions from light to dark)
        // In practice OpenCV could be used, but it requires an additional library

        // For now return image corners (no clear edges to detect)
        // In a real app you would use ML Kit Document Scanner (if available)
        return listOf(
            Point(0, 0),
            Point(width, 0),
            Point(width, height),
            Point(0, height)
        )
    }

    /**
     * Align document perspective
     * (Simplified - in practice it is better to use OpenCV)
     */
    fun cropAndAlign(bitmap: Bitmap, points: List<Point>): Bitmap {
        // If edges exist, crop to them
        if (points.size == 4) {
            val minX = points.minOf { it.x }.coerceAtLeast(0)
            val minY = points.minOf { it.y }.coerceAtLeast(0)
            val maxX = points.maxOf { it.x }.coerceAtMost(bitmap.width)
            val maxY = points.maxOf { it.y }.coerceAtMost(bitmap.height)

            val width = (maxX - minX).coerceAtLeast(10)
            val height = (maxY - minY).coerceAtLeast(10)

            return Bitmap.createBitmap(bitmap, minX, minY, width, height)
        }

        return bitmap
    }

    /**
     * Main document processing function
     */
    fun processDocument(bitmap: Bitmap): Bitmap {
        // 1. Preprocessing - contrast improvement
        val preprocessed = preprocessImage(bitmap)

        // 2. Edge detection
        val edges = detectDocumentEdges(preprocessed)

        // 3. Alignment
        val aligned = cropAndAlign(preprocessed, edges)

        return aligned
    }

    /**
     * Convert bitmap to black and white (threshold)
     * Useful for better OCR
     */
    fun binaryize(bitmap: Bitmap, threshold: Int = 128): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()

            pixels[i] = if (gray > threshold) Color.WHITE else Color.BLACK
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Calculate image sharpness (Laplacian variance)
     * Higher values = sharper image
     */
    fun estimateSharpness(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var laplacianSum = 0.0
        var count = 0

        // Laplacian kernel
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = getGray(pixels[y * width + x])
                val top = getGray(pixels[(y - 1) * width + x])
                val bottom = getGray(pixels[(y + 1) * width + x])
                val left = getGray(pixels[y * width + (x - 1)])
                val right = getGray(pixels[y * width + (x + 1)])

                val laplacian = (4 * center - top - bottom - left - right).toDouble()
                laplacianSum += laplacian * laplacian
                count++
            }
        }

        return if (count > 0) laplacianSum / count else 0.0
    }

    private fun getGray(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 0.299 + g * 0.587 + b * 0.114).toInt()
    }
}
