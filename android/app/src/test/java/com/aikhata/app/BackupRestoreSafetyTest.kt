package com.aikhata.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 33, 35])
class BackupRestoreSafetyTest {
    @Test
    fun invalidBackupIsRejectedBeforeExistingRecordsAreCleared() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            database.addCustomer("Existing customer", "03001234567")
            val invalidBackup = JSONObject()
                .put("format", "ai-khata-backup")
                .put("version", 2)
                .put("customers", JSONArray().put(
                    JSONObject()
                        .put("name", "Damaged customer")
                        .put("transactions", JSONArray().put(
                            JSONObject()
                                .put("type", "credit")
                                .put("amount", 0)
                                .put("date", "2026-11-17")
                        ))
                ))
                .toString()

            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                database.restoreBackup(invalidBackup)
            }
            assertEquals(listOf("Existing customer"), database.customers().map { it.name })
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }

    @Test
    fun backupKeepsCostSnapshotAfterItsProductWasDeleted() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val database = KhataDatabase(context)
        try {
            val customerId = database.addCustomer("Amina", "03001234567")
            val productId = database.addProduct(
                name = "Rice",
                sku = "R-01",
                unit = "kg",
                purchasePrice = 5.0,
                sellingPrice = 10.0,
                stockQuantity = 10.0,
                lowStockThreshold = 2.0
            )
            database.recordProductSale(
                customerId = customerId,
                productId = productId,
                quantity = 2.0,
                unitPrice = 10.0,
                description = "Two kg rice",
                date = "2026-11-17"
            )
            assertEquals(1, database.deleteProduct(productId))
            val before = database.profitAndLoss("2026-11")
            assertEquals(10.0, before.costOfGoodsSold, 0.001)
            assertEquals(10.0, before.grossProfit, 0.001)

            val backup = database.exportBackup()
            database.restoreBackup(backup)

            val after = database.profitAndLoss("2026-11")
            assertEquals(10.0, after.costOfGoodsSold, 0.001)
            assertEquals(10.0, after.grossProfit, 0.001)
            assertEquals(1, after.trackedSaleCount)
            assertTrue(database.customers().any { it.name == "Amina" })
        } finally {
            database.close()
            context.deleteDatabase("ai_khata_local.db")
        }
    }
}
