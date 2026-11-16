package com.aikhata.app

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun encrypt(plainText: String, password: String): String {
        require(password.length >= 8) { "Use a backup password with at least 8 characters." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", "ai-khata-encrypted-backup")
            .put("version", 1)
            .put("algorithm", "PBKDF2WithHmacSHA256-AES-256-GCM")
            .put("iterations", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString(2)
    }

    fun decrypt(fileText: String, password: String): String {
        val wrapper = JSONObject(fileText)
        require(wrapper.optString("format") == "ai-khata-encrypted-backup" &&
            wrapper.optInt("version") == 1
        ) { "This is not a supported encrypted AI Khata backup." }
        val salt = Base64.decode(wrapper.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(wrapper.getString("iv"), Base64.DEFAULT)
        val encrypted = Base64.decode(wrapper.getString("data"), Base64.DEFAULT)
        require(salt.size == 16 && iv.size == 12) { "The backup file is damaged." }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("The backup password is incorrect or the file is damaged.")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(bytes, "AES")
    }
}
