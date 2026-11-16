package com.aikhata.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikhata.app.CustomerRow
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    state: HomeUiState,
    onCustomers: () -> Unit,
    onAddCustomer: () -> Unit,
    onReports: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onBackup: () -> Unit,
    onRetryScans: () -> Unit,
    waitingPhotoCount: Int,
    onCustomerClick: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("AI Khata", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(state.dashboard?.shopName ?: "Your shop", fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Your shop at a glance", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                OutlinedButton(onClick = onLock) { Text("Lock") }
            }
        }
        if (state.loading && state.dashboard == null) {
            item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (state.error != null && state.dashboard == null) {
            item {
                Card {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRefresh) { Text("Try again") }
                    }
                }
            }
        } else {
            state.dashboard?.let { dashboard ->
                item { BalanceCard("Customers owe you", dashboard.outstanding, "Credit " + money(dashboard.totalCredit) + "  ·  Payments " + money(dashboard.totalPayments), emphasis = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BalanceCard("This month · sales", dashboard.monthCredit, "Payments " + money(dashboard.monthPayments), modifier = Modifier.weight(1f))
                        BalanceCard("Entries", null, "All recorded transactions", modifier = Modifier.weight(1f), count = dashboard.transactionCount.toString())
                    }
                }
                item {
                    Text("Quick actions", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCustomers, modifier = Modifier.weight(1f)) { Text("Customers") }
                        OutlinedButton(onClick = onAddCustomer, modifier = Modifier.weight(1f)) { Text("Add customer") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReports, modifier = Modifier.weight(1f)) { Text("Reports") }
                        OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Scan khata") }
                    }
                }
                if (waitingPhotoCount > 0) item {
                    OutlinedButton(onClick = onRetryScans, modifier = Modifier.fillMaxWidth()) { Text("Retry saved photo scans (" + waitingPhotoCount + ")") }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recently active", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("View all", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onCustomers))
                    }
                }
                if (dashboard.customers.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text("Your customer accounts will appear here. Add your first customer to begin.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(dashboard.customers, key = { it.id }) { customer ->
                        CustomerSummaryCard(customer) { onCustomerClick(customer.id) }
                    }
                }
                item {
                    OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Shop settings") }
                    OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Create a protected backup") }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(title: String, amount: Double?, detail: String, modifier: Modifier = Modifier, emphasis: Boolean = false, count: String? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (emphasis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(count ?: amount?.let(::money).orEmpty(), fontSize = if (emphasis) 24.sp else 19.sp, fontWeight = FontWeight.Bold, color = if (emphasis) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CustomerSummaryCard(customer: CustomerRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(customer.phone.ifBlank { "Customer account" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(if (customer.balance >= 0) "Due" else "Advance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(kotlin.math.abs(customer.balance)), color = if (customer.balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun money(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "PK"))
    format.minimumFractionDigits = 2
    format.maximumFractionDigits = 2
    return "Rs. " + format.format(value)
}
