package com.aikhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KhataDatabaseBusinessManagementTest {
    @Test
    fun inventorySaleReducesStockAndCalculatesCostBasedProfit() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            database.createOwner("Test shop", "123456")
            val customerId = database.addCustomer("Amina", "03001234567")
            val productId = database.addProduct("Rice", "R-01", "kg", 100.0, 150.0, 10.0, 3.0)

            database.recordProductSale(customerId, productId, 2.5, 160.0, "Rice sale", "2026-11-10")
            database.addExpense("2026-11-12", "Electricity", "Monthly bill", 50.0)
            database.addTransaction(customerId, "credit", 999.0, "Manual legacy sale", "2026-11-13")

            assertEquals(7.5, database.products().single().stockQuantity, 0.001)
            val sale = database.transactions(customerId).first { it.productId == productId }
            assertEquals(400.0, sale.amount, 0.001)
            assertEquals(2.5, sale.quantity!!, 0.001)
            assertEquals(100.0, sale.unitCost!!, 0.001)

            val report = database.profitAndLoss("2026-11")
            assertEquals(400.0, report.trackedSales, 0.001)
            assertEquals(250.0, report.costOfGoodsSold, 0.001)
            assertEquals(150.0, report.grossProfit, 0.001)
            assertEquals(50.0, report.expenses, 0.001)
            assertEquals(100.0, report.netProfit, 0.001)
            assertEquals(750.0, report.inventoryValue, 0.001)
            assertEquals(1, report.expenseCount)
            assertEquals(1, report.trackedSaleCount)

            database.deleteProduct(productId)
            val afterProductDeletion = database.profitAndLoss("2026-11")
            assertEquals(400.0, afterProductDeletion.trackedSales, 0.001)
            assertEquals(250.0, afterProductDeletion.costOfGoodsSold, 0.001)
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }

    @Test
    fun failedSaleDoesNotChangeStockOrCreateLedgerEntry() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            database.createOwner("Test shop", "123456")
            val customerId = database.addCustomer("Amina", "")
            val productId = database.addProduct("Tea", "", "pack", 20.0, 30.0, 2.0, 1.0)

            var failed = false
            try {
                database.recordProductSale(customerId, productId, 3.0, 30.0, "", "2026-11-10")
            } catch (_: IllegalArgumentException) {
                failed = true
            }

            assertTrue(failed)
            assertEquals(2.0, database.products().single().stockQuantity, 0.001)
            assertTrue(database.transactions(customerId).isEmpty())
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }

    @Test
    fun versionTwoBackupRestoresProductsExpensesAndProductSaleCostSnapshots() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            database.createOwner("Test shop", "123456")
            val customerId = database.addCustomer("Amina", "")
            val productId = database.addProduct("Flour", "F-1", "kg", 80.0, 110.0, 8.0, 2.0)
            database.recordProductSale(customerId, productId, 1.5, 110.0, "Flour", "2026-11-08")
            database.addExpense("2026-11-09", "Transport", "Delivery", 75.0)

            val backup = database.exportBackup()
            assertTrue(backup.contains("\"version\": 2"))
            database.addExpense("2026-11-10", "Other", "Temporary", 5.0)
            database.restoreBackup(backup)

            assertEquals(1, database.products().size)
            assertEquals(6.5, database.products().single().stockQuantity, 0.001)
            assertEquals(1, database.expenses().size)
            assertEquals(1, database.transactions(database.customers().single().id).size)
            val report = database.profitAndLoss("2026-11")
            assertEquals(165.0, report.trackedSales, 0.001)
            assertEquals(120.0, report.costOfGoodsSold, 0.001)
            assertEquals(75.0, report.expenses, 0.001)
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }
}
