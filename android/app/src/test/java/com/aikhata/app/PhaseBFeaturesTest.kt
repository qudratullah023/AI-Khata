package com.aikhata.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33, 35])
class PhaseBFeaturesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun customerTagsNotesAndOptInAreStoredPerCustomer() {
        val store = PhaseBStore(context, "phase_b_meta_test")
        try {
            store.saveCustomerMeta(CustomerMeta(42L, "neighbour, wholesale", "Call after 5 pm", true))
            val saved = store.customerMeta(42L)
            assertEquals("neighbour, wholesale", saved.tags)
            assertEquals("Call after 5 pm", saved.notes)
            assertTrue(saved.shareOptIn)
            assertFalse(store.customerMeta(43L).shareOptIn)
        } finally {
            store.close()
            context.deleteDatabase("ai_khata_phase_b_phase_b_meta_test.db")
        }
    }

    @Test
    fun staffPinIsSaltedAndVerifiedWithoutExposingThePin() {
        val store = PhaseBStore(context, "phase_b_staff_test")
        try {
            store.setStaffPin("731946")
            assertTrue(store.hasStaffPin())
            assertTrue(store.verifyStaffPin("731946"))
            assertFalse(store.verifyStaffPin("000000"))
            store.clearStaffPin()
            assertFalse(store.hasStaffPin())
            assertFalse(store.verifyStaffPin("731946"))
        } finally {
            store.close()
            context.deleteDatabase("ai_khata_phase_b_phase_b_staff_test.db")
        }
    }

    @Test
    fun supplierPayablesIncreaseBalanceAndPaymentsReduceIt() {
        val store = PhaseBStore(context, "phase_b_supplier_test")
        try {
            val supplierId = store.addSupplier("Wholesale Market", "03001234567")
            store.addSupplierEntry(supplierId, "payable", 12500.0, "Stock purchase", "2026-10-01")
            assertEquals(12500.0, store.suppliers().single().balance, 0.001)
            store.addSupplierEntry(supplierId, "payment", 4500.0, "Bank transfer", "2026-10-03")
            assertEquals(8000.0, store.suppliers().single().balance, 0.001)
        } finally {
            store.close()
            context.deleteDatabase("ai_khata_phase_b_phase_b_supplier_test.db")
        }
    }

    @Test
    fun supplierReminderCountsOnlyUnpaidAmountsPastCutoff() {
        val store = PhaseBStore(context, "phase_b_overdue_test")
        try {
            val oldSupplier = store.addSupplier("Old supplier", "")
            store.addSupplierEntry(oldSupplier, "payable", 12500.0, "October stock", "2026-10-01")
            store.addSupplierEntry(oldSupplier, "payable", 3000.0, "November stock", "2026-11-01")
            store.addSupplierEntry(oldSupplier, "payment", 13000.0, "Partial settlement", "2026-11-02")

            val overdueSupplier = store.addSupplier("Overdue supplier", "")
            store.addSupplierEntry(overdueSupplier, "payable", 1000.0, "Old balance", "2026-10-02")

            val summary = store.overdueSupplierSummary("2026-10-18")
            assertEquals(1, summary.first)
            assertEquals(500.0, summary.second, 0.001)
        } finally {
            store.close()
            context.deleteDatabase("ai_khata_phase_b_phase_b_overdue_test.db")
        }
    }

    @Test
    fun advancePaymentsOffsetLaterCustomerAndSupplierCredits() {
        val name = "phase_b_advance_test.db"
        context.deleteDatabase(name)
        val db = KhataDatabase(context, name)
        val store = PhaseBStore(context, "phase_b_advance_test")
        try {
            val customerId = db.addCustomer("Hina", "03005550777")
            db.addTransaction(customerId, "payment", 1000.0, "Advance", "2026-10-01")
            db.addTransaction(customerId, "credit", 1000.0, "Later purchase", "2026-11-01")
            assertEquals(0, db.overdueCustomerSummary("2026-10-18").first)

            val supplierId = store.addSupplier("Prepaid supplier", "")
            store.addSupplierEntry(supplierId, "payment", 1000.0, "Advance", "2026-10-01")
            store.addSupplierEntry(supplierId, "payable", 1000.0, "Later stock", "2026-11-01")
            assertEquals(0, store.overdueSupplierSummary("2026-10-18").first)
        } finally {
            db.close()
            store.close()
            context.deleteDatabase(name)
            context.deleteDatabase("ai_khata_phase_b_phase_b_advance_test.db")
        }
    }

    @Test
    fun newShopDatabaseDoesNotLeakCustomersFromDefaultShop() {
        val defaultName = "phase_b_default_isolation.db"
        val secondName = "phase_b_second_isolation.db"
        context.deleteDatabase(defaultName)
        context.deleteDatabase(secondName)
        val first = KhataDatabase(context, defaultName)
        val second = KhataDatabase(context, secondName)
        try {
            first.createOwner("First shop", "111111")
            second.createOwner("Second shop", "222222")
            first.addCustomer("First customer", "03001111111")
            second.addCustomer("Second customer", "03002222222")
            assertEquals(listOf("First customer"), first.customers().map { it.name })
            assertEquals(listOf("Second customer"), second.customers().map { it.name })
            assertTrue(first.verifyPin("111111"))
            assertFalse(first.verifyPin("222222"))
        } finally {
            first.close()
            second.close()
            context.deleteDatabase(defaultName)
            context.deleteDatabase(secondName)
        }
    }

    @Test
    fun customerReminderKeepsOldUnpaidCreditVisibleAfterARecentSale() {
        val name = "phase_b_overdue_customer_test.db"
        context.deleteDatabase(name)
        val db = KhataDatabase(context, name)
        try {
            val customerId = db.addCustomer("Nadia", "03005550999")
            db.addTransaction(customerId, "credit", 1000.0, "Old credit", "2026-10-01")
            db.addTransaction(customerId, "credit", 500.0, "Recent credit", "2026-11-02")
            db.addTransaction(customerId, "payment", 500.0, "Partial payment", "2026-11-03")
            val summary = db.overdueCustomerSummary("2026-10-18")
            assertEquals(1, summary.first)
            assertEquals(1000.0, summary.second, 0.001)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun universalSearchAndDailySummaryFindLocalLedgerRecords() {
        val name = "phase_b_search_test.db"
        context.deleteDatabase(name)
        val db = KhataDatabase(context, name)
        try {
            val customerId = db.addCustomer("Amina Khan", "03005550123")
            db.addProduct("Rice", "R-101", "kg", 200.0, 250.0, 10.0, 2.0)
            db.addTransaction(customerId, "credit", 1250.0, "Rice order", "2026-11-17")
            db.addTransaction(customerId, "payment", 250.0, "Cash received", "2026-11-17")

            assertTrue(db.searchEverything("03005550123").any { it.kind == "Customer" })
            assertTrue(db.searchEverything("Rice").any { it.kind == "Product" })
            assertTrue(db.searchEverything("2026-11-17").any { it.kind == "Entry" })
            assertTrue(db.searchEverything("1250").any { it.kind == "Entry" })

            val summary = db.dailySummary("2026-11-17")
            assertEquals(1250.0, summary.first, 0.001)
            assertEquals(250.0, summary.second, 0.001)
            assertEquals(1000.0, summary.third, 0.001)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
