package com.aikhata.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ReportsScreen(
    initialMonth: String,
    viewModel: ReportsViewModel,
    onBack: () -> Unit,
    onShareReport: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var monthText by remember(initialMonth) { mutableStateOf(initialMonth) }
    var monthError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialMonth) { viewModel.load(initialMonth) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Text(
                        "Reports",
                        Modifier.weight(1f).padding(start = 12.dp),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { viewModel.load(monthText) }) { Text("Refresh") }
                }
                Text(
                    "All-time and monthly activity from this phone's ledger.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Choose a month", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = monthText,
                            onValueChange = {
                                monthText = it
                                monthError = null
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("YYYY-MM") },
                            placeholder = { Text("2026-11") }
                        )
                        Button(onClick = {
                            if (validMonth(monthText)) viewModel.load(monthText)
                            else monthError = "Enter a month as YYYY-MM, for example 2026-11."
                        }) { Text("Apply") }
                    }
                    if (monthError != null) {
                        Text(monthError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
            if (state.loading && state.snapshot == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.error != null && state.snapshot == null) {
                item {
                    Card {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.load(monthText) }) { Text("Try again") }
                        }
                    }
                }
            } else {
                state.snapshot?.let { report ->
                    item {
                        Text("All-time summary", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            MetricCard("Total sales", money(report.totalCredit), Modifier.weight(1f))
                            MetricCard("Payments", money(report.totalPayments), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            MetricCard("Amount due", money(report.outstanding), Modifier.weight(1f), emphasis = true)
                            MetricCard("Entries", report.transactionCount.toString(), Modifier.weight(1f))
                        }
                        Text(
                            report.customersOwing.toString() + " customers currently have an amount due.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    item {
                        Text("Activity in " + report.month, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        MetricCard("Sales / credit", money(report.monthCredit), Modifier.fillMaxWidth())
                        MetricCard("Payments received", money(report.monthPayments), Modifier.fillMaxWidth())
                        MetricCard("Net change in amount due", money(report.monthNet), Modifier.fillMaxWidth(), emphasis = true)
                        Text(
                            report.monthTransactionCount.toString() + " transactions recorded in this month.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    item {
                        Text("Monthly comparison", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        val maxValue = maxOf(report.monthCredit, report.monthPayments, 1.0)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Sales", fontSize = 12.sp)
                            LinearProgressIndicator(
                                progress = (report.monthCredit / maxValue).toFloat().coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Payments", fontSize = 12.sp)
                            LinearProgressIndicator(
                                progress = (report.monthPayments / maxValue).toFloat().coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Customers with highest due", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(report.topCustomers.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (report.topCustomers.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text("No outstanding customer balances.", Modifier.padding(16.dp))
                            }
                        }
                    } else {
                        items(report.topCustomers, key = { it.id }) { customer ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(customer.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            customer.phone.ifBlank { "Customer account" },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(money(customer.balance), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { onShareReport(report.month) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Share this report") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (emphasis) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun validMonth(value: String): Boolean {
    if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(value)) return false
    return value.substring(0, 4).toIntOrNull() in 1900..9999
}

private fun money(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "PK"))
    format.minimumFractionDigits = 2
    format.maximumFractionDigits = 2
    return "Rs. " + format.format(value)
}
