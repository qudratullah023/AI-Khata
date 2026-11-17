package com.aikhata.app

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupCryptoTest {
    @Test
    fun encryptedBackupRoundTripsWithTheSamePassword() {
        val original = """{"format":"ai-khata-backup","version":1,"customers":[{"name":"Amina"}]}"""
        val encrypted = BackupCrypto.encrypt(original, "shop-backup-2026")
        assertNotEquals(original, encrypted)
        assertEquals(original, BackupCrypto.decrypt(encrypted, "shop-backup-2026"))
    }

    @Test
    fun encryptedBackupRejectsAnIncorrectPassword() {
        val encrypted = BackupCrypto.encrypt("private khata records", "shop-backup-2026")
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(encrypted, "wrong-password")
        }
    }

    @Test
    fun encryptedBackupRejectsTamperedCiphertext() {
        val wrapper = JSONObject(BackupCrypto.encrypt("private shop records", "shop-backup-2026"))
        val bytes = Base64.decode(wrapper.getString("data"), Base64.DEFAULT)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        wrapper.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(wrapper.toString(), "shop-backup-2026")
        }
    }

    @Test
    fun largeBackupPayloadCanBeEncryptedAndRestored() {
        val original = "customer,transaction,amount,date\n".repeat(125_000)
        val encrypted = BackupCrypto.encrypt(original, "shop-backup-2026")
        assertEquals(original, BackupCrypto.decrypt(encrypted, "shop-backup-2026"))
    }

    @Test
    fun backupEncryptionRequiresAtLeastEightPasswordCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt("data", "short")
        }
    }
}
