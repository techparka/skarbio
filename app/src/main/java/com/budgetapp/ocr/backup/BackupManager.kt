package com.budgetapp.ocr.backup

import android.content.Context
import android.net.Uri
import com.budgetapp.ocr.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.spec.PBEKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {

    companion object {
        private val MAGIC = byteArrayOf(0x53, 0x4B, 0x42, 0x00) // "SKB\0"
        private const val VERSION: Byte = 1
        private const val ITERATIONS = 100_000
        private const val KEY_SIZE = 256
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val GCM_TAG_SIZE = 128
    }

    suspend fun backup(outputUri: Uri, password: String) {
        // 1. Flush WAL to main DB file
        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")

        // 2. Read DB file bytes
        val dbFile = context.getDatabasePath("budget_database")
        val dbBytes = dbFile.readBytes()

        // 3. Build output payload
        val payload = if (password.isNotEmpty()) {
            encryptPayload(dbBytes, password)
        } else {
            MAGIC + byteArrayOf(VERSION, 0) + dbBytes
        }

        // 4. Write to chosen URI
        context.contentResolver.openOutputStream(outputUri)?.use { stream ->
            stream.write(payload)
        } ?: throw Exception("Cannot open output stream")
    }

    suspend fun restore(inputUri: Uri, password: String) {
        // 1. Read backup file
        val data = context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
            ?: throw Exception("Cannot open input stream")

        // 2. Verify magic bytes
        if (data.size < 6 || !data.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw IllegalArgumentException("Not a valid .skb backup file")
        }

        val encrypted = data[5].toInt()

        // 3. Decrypt or extract raw DB bytes
        val dbBytes = if (encrypted == 1) {
            decryptPayload(data, password)
        } else {
            data.copyOfRange(6, data.size)
        }

        // 4. Close DB and overwrite the file
        db.close()
        val dbFile = context.getDatabasePath("budget_database")
        dbFile.writeBytes(dbBytes)

        // Remove WAL/SHM auxiliary files so Room starts clean
        context.getDatabasePath("budget_database-wal").delete()
        context.getDatabasePath("budget_database-shm").delete()

        // 5. Restart app — cleanest way to reload a replaced DB
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // ── Encryption helpers ────────────────────────────────────────────────────

    private fun encryptPayload(plainBytes: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        // Layout: magic(4) + version(1) + encrypted(1) + salt(16) + iv(12) + ciphertext
        return MAGIC + byteArrayOf(VERSION, 1) + salt + iv + ciphertext
    }

    private fun decryptPayload(data: ByteArray, password: String): ByteArray {
        val minSize = 6 + SALT_SIZE + IV_SIZE
        if (data.size < minSize) throw IllegalArgumentException("Backup file too short")

        val salt = data.copyOfRange(6, 6 + SALT_SIZE)
        val iv = data.copyOfRange(6 + SALT_SIZE, 6 + SALT_SIZE + IV_SIZE)
        val ciphertext = data.copyOfRange(6 + SALT_SIZE + IV_SIZE, data.size)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw SecurityException("Wrong password or corrupted file")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_SIZE)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
