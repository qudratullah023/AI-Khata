package com.aikhata.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Bounded backup reader. A malformed or unexpectedly huge document must not be allowed
 * to grow without limit in memory on a shopkeeper's phone.
 */
object BackupFileIO {
    const val MAX_BYTES: Long = 32L * 1024L * 1024L

    fun readUtf8(input: InputStream, maxBytes: Long = MAX_BYTES): String {
        require(maxBytes > 0L) { "The backup size limit is invalid." }
        input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= maxBytes) {
                    "This backup is too large to open safely on this phone. The maximum supported file size is 32 MB."
                }
                output.write(buffer, 0, count)
            }
            return String(output.toByteArray(), StandardCharsets.UTF_8)
        }
    }
}
