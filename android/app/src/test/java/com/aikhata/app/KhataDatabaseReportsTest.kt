package com.aikhata.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KhataDatabaseReportsTest {
    @Test
    fun monthlyTransactionCountUsesTheSelectedCalendarMonth() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            database.createOwner("Test shop", "123456")
            val customerId = database.addCustomer("Amina", "03001234567")
            database.addTransaction(customerId, "credit", 1200.0, "Rice", "2026-11-02")
            database.addTransaction(customerId, "payment", 200.0, "Cash", "2026-11-05")
            database.addTransaction(customerId, "credit", 500.0, "Sugar", "2026-10-31")

            assertEquals(2, database.monthTransactionCount("2026-11"))
            assertEquals(1, database.monthTransactionCount("2026-10"))
            assertEquals(0, database.monthTransactionCount("2026-09"))
            assertEquals(1400.0, database.monthTotals("2026-11").first, 0.001)
            assertEquals(200.0, database.monthTotals("2026-11").second, 0.001)
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }
}
