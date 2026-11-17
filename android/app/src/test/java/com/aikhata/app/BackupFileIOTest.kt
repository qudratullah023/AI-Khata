package com.aikhata.app

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFileIOTest {
    @Test
    fun readsLargeBackupWithinTheConfiguredLimit() {
        val expected = "customer-entry,".repeat(140_000)
        val actual = BackupFileIO.readUtf8(
            ByteArrayInputStream(expected.toByteArray(Charsets.UTF_8)),
            maxBytes = expected.toByteArray(Charsets.UTF_8).size.toLong()
        )
        assertEquals(expected, actual)
    }

    @Test
    fun rejectsInputThatExceedsTheLimit() {
        val input = ByteArrayInputStream(ByteArray(4097) { 'x'.code.toByte() })
        assertThrows(IllegalArgumentException::class.java) {
            BackupFileIO.readUtf8(input, maxBytes = 4096)
        }
    }
}
