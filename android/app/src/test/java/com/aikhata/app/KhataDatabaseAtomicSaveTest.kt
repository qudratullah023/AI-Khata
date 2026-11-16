package com.aikhata.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KhataDatabaseAtomicSaveTest {
    @Test
    fun failedBatchDoesNotLeavePartialCustomerOrTransactionRecords() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")
        val helper = KhataDatabase(context)

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            helper.inTransaction {
                val customerId = helper.addCustomer("Amina", "")
                helper.addTransaction(customerId, "credit", 100.0, "Rice", "2026-11-16")
                helper.addTransaction(customerId, "credit", Double.NaN, "Invalid", "2026-11-16")
            }
        }

        assertTrue(helper.customers().isEmpty())
        helper.close()
        context.deleteDatabase("ai_khata_local.db")
    }
}
