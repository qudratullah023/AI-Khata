package com.aikhata.app

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
    fun backupEncryptionRequiresAtLeastEightPasswordCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt("data", "short")
        }
    }
}
