package com.aikhata.app

import java.text.SimpleDateFormat
import java.util.Locale

object LedgerMath {
    fun parseAmount(raw: String): Double? {
        val normalized = raw.trim().map { character ->
            when (character) {
                in '۰'..'۹' -> ('0'.code + character.code - '۰'.code).toChar()
                in '٠'..'٩' -> ('0'.code + character.code - '٠'.code).toChar()
                else -> character
            }
        }.joinToString("")
            .replace(",", "")
            .replace("٬", "")
            .replace("٫", ".")
            .replace(Regex("\\s+"), "")

        val amount = normalized.toDoubleOrNull() ?: return null
        return amount.takeIf {
            it.isFinite() && it > 0.0 && it <= 9_000_000_000_000.0
        }
    }

    fun calculateBalance(creditPaisa: Long, paymentPaisa: Long): Long =
        creditPaisa - paymentPaisa

    fun isValidDate(value: String): Boolean {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }
            val parsed = formatter.parse(value) ?: return false
            formatter.format(parsed) == value
        } catch (_: Exception) {
            false
        }
    }
}
