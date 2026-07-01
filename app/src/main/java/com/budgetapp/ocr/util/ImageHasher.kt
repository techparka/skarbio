package com.budgetapp.ocr.util

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Computes a perceptual hash of an image (Average Hash algorithm).
 * Used for duplicate receipt detection.
 */
object ImageHasher {

    /**
     * Computes the Average Hash (aHash) for an image.
     * @param bitmap Receipt bitmap
     * @return Hash as a String (64 hex characters)
     */
    fun calculateHash(bitmap: Bitmap): String {
        // 1. Resize to 8x8 pixels
        val resized = Bitmap.createScaledBitmap(bitmap, 8, 8, true)

        // 2. Convert to grayscale and compute average brightness
        val pixels = IntArray(64)
        var sum = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val pixel = resized.getPixel(x, y)
                val gray = toGrayscale(pixel)
                pixels[y * 8 + x] = gray
                sum += gray
            }
        }
        val average = sum / 64

        // 3. Build hash: 1 if pixel > average, 0 if <=
        val hash = StringBuilder()
        for (i in 0 until 64 step 4) {
            var hex = 0
            for (j in 0 until 4) {
                if (pixels[i + j] > average) {
                    hex = hex or (1 shl (3 - j))
                }
            }
            hash.append(hex.toString(16))
        }

        resized.recycle()
        return hash.toString()
    }

    /**
     * Computes the Hamming distance between two hashes
     * (number of differing bits).
     * @return Number of differing bits (0-64)
     */
    fun hammingDistance(hash1: String, hash2: String): Int {
        if (hash1.length != hash2.length) return 64 // Maximum difference

        var distance = 0
        for (i in hash1.indices) {
            val bits1 = hash1[i].toString().toInt(16)
            val bits2 = hash2[i].toString().toInt(16)
            val xor = bits1 xor bits2

            // Count set bits
            var count = xor
            while (count > 0) {
                distance += count and 1
                count = count shr 1
            }
        }
        return distance
    }

    /**
     * Checks whether two hashes are similar (duplicates).
     * @param threshold Maximum allowed difference (0-64). Default: 5
     * @return true if images are likely identical
     */
    fun areSimilar(hash1: String, hash2: String, threshold: Int = 5): Boolean {
        return hammingDistance(hash1, hash2) <= threshold
    }

    private fun toGrayscale(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (r * 0.299 + g * 0.587 + b * 0.114).toInt()
    }
}
