package com.aikhata.app

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object LedgerPhotoProcessor {
    private const val MAX_SOURCE_BYTES = 10 * 1024 * 1024
    private const val MAX_LONG_EDGE = 1600

    fun prepare(contentResolver: ContentResolver, uri: Uri): ByteArray {
        val source = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("The selected photo could not be opened.")
        require(source.isNotEmpty()) { "The selected photo is empty." }
        require(source.size <= MAX_SOURCE_BYTES) { "Choose a photo smaller than 10 MB." }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This is not a readable image." }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_LONG_EDGE * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options)
            ?: throw IllegalArgumentException("The photo could not be decoded.")

        val orientation = try {
            ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val corrected = if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else decoded

        val longEdge = maxOf(corrected.width, corrected.height)
        val outputBitmap = if (longEdge > MAX_LONG_EDGE) {
            val scale = MAX_LONG_EDGE.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                corrected,
                (corrected.width * scale).toInt().coerceAtLeast(1),
                (corrected.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== corrected) corrected.recycle() }
        } else corrected

        return try {
            ByteArrayOutputStream().use { output ->
                check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    "The photo could not be compressed."
                }
                output.toByteArray()
            }
        } finally {
            outputBitmap.recycle()
        }
    }
}
