package com.aikhata.app.data

import com.aikhata.app.CustomerRow
import com.aikhata.app.KhataDatabase
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

class KhataRepository(private val database: KhataDatabase) {
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
}
