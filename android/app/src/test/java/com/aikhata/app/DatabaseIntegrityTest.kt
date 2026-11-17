package com.aikhata.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 33, 35])
class DatabaseIntegrityTest {
    @Test
    fun newAndPopulatedDatabasePassesReadOnlyIntegrityCheck() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            assertTrue(database.checkIntegrity())
            val customerId = database.addCustomer("Amina", "03001234567")
            database.addTransaction(customerId, "credit", 1250.0, "Groceries", "2026-11-17")
            assertTrue(database.checkIntegrity())
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }

    @Test
    fun integrityCheckFindsForeignKeyViolationsAndDoesNotDeleteRows() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            val writable = database.writableDatabase
            writable.execSQL("PRAGMA foreign_keys=OFF")
            writable.execSQL(
                "INSERT INTO transactions(customer_id,transaction_date,transaction_type,description,amount_paisa,created_at) " +
                    "VALUES (999,'2026-11-17','credit','test row',100,'2026-11-17T00:00:00')"
            )

            assertFalse(database.checkIntegrity())
            val rowCount = writable.rawQuery("SELECT COUNT(*) FROM transactions", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertTrue("The health check must not delete records.", rowCount == 1)
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }
}
