package com.aikhata.app.data

import com.aikhata.app.CustomerRow
import com.aikhata.app.KhataDatabase
import com.aikhata.app.TransactionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardSnapshot(
    val shopName: String,
    val totalCredit: Double,
    val totalPayments: Double,
    val transactionCount: Int,
    val monthCredit: Double,
    val monthPayments: Double,
    val customers: List<CustomerRow>
) {
    val outstanding: Double get() = totalCredit - totalPayments
}

data class CustomerDetailSnapshot(
    val customer: CustomerRow,
    val transactions: List<TransactionRow>
) {
    val totalCredit: Double get() = transactions.filter { it.type == "credit" }.sumOf { it.amount }
    val totalPayments: Double get() = transactions.filter { it.type == "payment" }.sumOf { it.amount }
    val balance: Double get() = totalCredit - totalPayments
}

data class ReportsSnapshot(
    val month: String,
    val totalCredit: Double,
    val totalPayments: Double,
    val transactionCount: Int,
    val monthCredit: Double,
    val monthPayments: Double,
    val monthTransactionCount: Int,
    val customersOwing: Int,
    val topCustomers: List<CustomerRow>
) {
    val outstanding: Double get() = totalCredit - totalPayments
    val monthNet: Double get() = monthCredit - monthPayments
}

class KhataRepository(private val database: KhataDatabase) {
    suspend fun loadCustomers(query: String = ""): List<CustomerRow> =
        withContext(Dispatchers.IO) { database.customers(query) }

    suspend fun loadDashboard(): DashboardSnapshot = withContext(Dispatchers.IO) {
        val totals = database.totals()
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val monthTotals = database.monthTotals(month)
        DashboardSnapshot(
            shopName = database.shopName(),
            totalCredit = totals.first,
            totalPayments = totals.second,
            transactionCount = totals.third,
            monthCredit = monthTotals.first,
            monthPayments = monthTotals.second,
            customers = database.customers().sortedByDescending { it.id }.take(5)
        )
    }

    suspend fun loadCustomerDetail(customerId: Long): CustomerDetailSnapshot? =
        withContext(Dispatchers.IO) {
            val customer = database.customer(customerId) ?: return@withContext null
            CustomerDetailSnapshot(customer, database.transactions(customerId))
        }

    suspend fun loadReports(month: String): ReportsSnapshot = withContext(Dispatchers.IO) {
        val totals = database.totals()
        val monthTotals = database.monthTotals(month)
        val customers = database.customers()
        ReportsSnapshot(
            month = month,
            totalCredit = totals.first,
            totalPayments = totals.second,
            transactionCount = totals.third,
            monthCredit = monthTotals.first,
            monthPayments = monthTotals.second,
            monthTransactionCount = database.monthTransactionCount(month),
            customersOwing = customers.count { it.balance > 0.009 },
            topCustomers = customers.filter { it.balance > 0.009 }
                .sortedByDescending { it.balance }.take(5)
        )
    }
}
