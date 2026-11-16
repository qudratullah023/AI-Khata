package com.aikhata.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikhata.app.ui.components.AnimatedValue
import com.aikhata.app.ui.components.Eyebrow
import com.aikhata.app.ui.components.FuturisticBackdrop
import com.aikhata.app.ui.components.GlowPill
import com.aikhata.app.ui.components.NeonPanel
import com.aikhata.app.ui.components.ProgressGlow
import com.aikhata.app.ui.components.ScreenEntrance
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

    FuturisticBackdrop {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 780.dp).padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 15.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                item {
                    ScreenEntrance {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = onBack) { Text("Back") }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Eyebrow("Business intelligence")
                                Text("Reports", style = MaterialTheme.typography.headlineMedium)
                            }
                            GlowPill("LIVE")
                        }
                    }
                }
                item {
                    ScreenEntrance(delayMillis = 50) {
                        NeonPanel(Modifier.fillMaxWidth(), radius = 20.dp) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Eyebrow("Reporting period")
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = monthText,
                                        onValueChange = {
                                            monthText = it
                                            monthError = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        label = { Text("Month · YYYY-MM") },
                                        placeholder = { Text("2026-11") }
                                    )
                                    Button(onClick = {
                                        if (validMonth(monthText)) {
                                            monthError = null
                                            viewModel.load(monthText)
                                        } else monthError = "Use YYYY-MM, for example 2026-11."
                                    }) { Text("Apply") }
                                }
                                if (monthError != null) {
                                    Text(monthError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                                Text(
                                    "Reports are calculated from records stored on this phone.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (state.loading && state.snapshot == null) {
                    item { Box(Modifier.fillMaxWidth().padding(35.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (state.error != null && state.snapshot == null) {
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                Button(onClick = { viewModel.load(monthText) }) { Text("Try again") }
                            }
                        }
                    }
                } else {
                    state.snapshot?.let { report ->
                        item {
                            ScreenEntrance(delayMillis = 100) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Eyebrow("Lifetime snapshot")
                                            Text("All-time performance", style = MaterialTheme.typography.titleLarge)
                                        }
                                        GlowPill("${report.transactionCount} ENTRIES")
                                    }
                                    NeonPanel(Modifier.fillMaxWidth(), radius = 23.dp) {
                                        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Column {
                                                Text("NET OUTSTANDING", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.1.sp)
                                                AnimatedValue(money(kotlin.math.abs(report.outstanding)), size = 28)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                                MetricCard("Total sales", money(report.totalCredit), Modifier.weight(1f))
                                                MetricCard("Payments", money(report.totalPayments), Modifier.weight(1f))
                                            }
                                            Text(
                                                "${report.customersOwing} customer(s) currently have a balance due.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            ScreenEntrance(delayMillis = 160) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Eyebrow("Period analytics")
                                    Text("Activity · ${report.month}", style = MaterialTheme.typography.titleLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        MetricCard("Sales / credit", money(report.monthCredit), Modifier.weight(1f))
                                        MetricCard("Payments", money(report.monthPayments), Modifier.weight(1f))
                                    }
                                    MetricCard(
                                        "Net balance change",
                                        money(report.monthNet),
                                        Modifier.fillMaxWidth(),
                                        emphasis = true
                                    )
                                    Text(
                                        "${report.monthTransactionCount} transaction(s) in this period.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        item {
                            ScreenEntrance(delayMillis = 210) {
                                NeonPanel(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Eyebrow("Cash flow comparison")
                                        Text("Sales vs payments", style = MaterialTheme.typography.titleMedium)
                                        val maxValue = maxOf(report.monthCredit, report.monthPayments, 1.0)
                                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Sales", fontSize = 12.sp)
                                                Text(money(report.monthCredit), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            ProgressGlow((report.monthCredit / maxValue).toFloat())
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Payments", fontSize = 12.sp)
                                                Text(money(report.monthPayments), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            ProgressGlow((report.monthPayments / maxValue).toFloat())
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            ScreenEntrance(delayMillis = 260) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Eyebrow("Receivables")
                                        Text("Highest outstanding", style = MaterialTheme.typography.titleLarge)
                                    }
                                    GlowPill("${report.topCustomers.size} ACCOUNTS")
                                }
                            }
                        }
                        if (report.topCustomers.isEmpty()) {
                            item {
                                NeonPanel(Modifier.fillMaxWidth()) {
                                    Text(
                                        "No outstanding customer balances.",
                                        Modifier.padding(18.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(report.topCustomers, key = { it.id }) { customer ->
                                NeonPanel(Modifier.fillMaxWidth(), radius = 17.dp) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(35.dp).clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(customer.name.take(1).uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                            Text(customer.name, fontWeight = FontWeight.SemiBold)
                                            Text(customer.phone.ifBlank { "Customer account" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                        }
                                        Text(money(customer.balance), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        item {
                            ScreenEntrance(delayMillis = 320) {
                                Button(onClick = { onShareReport(report.month) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Share report")
                                }
                            }
                        }
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
    NeonPanel(modifier, radius = 17.dp) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun validMonth(value: String): Boolean {
    if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(value)) return false
    val year = value.substring(0, 4).toIntOrNull() ?: return false
    return year in 1900..9999
}

private fun money(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "PK"))
    format.minimumFractionDigits = 2
    format.maximumFractionDigits = 2
    return "Rs. " + format.format(value)
}
