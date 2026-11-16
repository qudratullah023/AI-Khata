package com.aikhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMathTest {
    @Test
    fun parsesCommaSeparatedAndDecimalAmounts() {
        assertEquals(1200.0, LedgerMath.parseAmount(" 1,200 ")!!, 0.001)
        assertEquals(1200.50, LedgerMath.parseAmount("1200.50")!!, 0.001)
        assertEquals(1250.0, LedgerMath.parseAmount("1\t250")!!, 0.001)
    }

    @Test
    fun parsesUrduAndArabicIndicDigits() {
        assertEquals(1200.0, LedgerMath.parseAmount("۱۲\u06f0۰")!!, 0.001)
        assertEquals(1200.0, LedgerMath.parseAmount("١٢٠٠")!!, 0.001)
        assertEquals(1200.50, LedgerMath.parseAmount("۱٬۲۰۰٫۵۰")!!, 0.001)
    }

    @Test
    fun rejectsInvalidOrNonPositiveAmounts() {
        assertNull(LedgerMath.parseAmount("not an amount"))
        assertNull(LedgerMath.parseAmount("-500"))
        assertNull(LedgerMath.parseAmount("0"))
    }

    @Test
    fun calculatesBalanceWithoutFloatingPointStorage() {
        assertEquals(125000L, LedgerMath.calculateBalance(150000L, 25000L))
        assertEquals(-5000L, LedgerMath.calculateBalance(20000L, 25000L))
    }

    @Test
    fun validatesCalendarDates() {
        assertTrue(LedgerMath.isValidDate("2024-02-29"))
        assertTrue(LedgerMath.isValidDate("2026-11-16"))
        assertFalse(LedgerMath.isValidDate("2026-02-29"))
        assertFalse(LedgerMath.isValidDate("16-11-2026"))
    }
}
