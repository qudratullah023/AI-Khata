package com.aikhata.app.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikhata.app.TransactionRow
import com.aikhata.app.ui.components.AnimatedValue
import com.aikhata.app.ui.components.Eyebrow
import com.aikhata.app.ui.components.FuturisticBackdrop
import com.aikhata.app.ui.components.GlowPill
import com.aikhata.app.ui.components.NeonPanel
import com.aikhata.app.ui.components.ScreenEntrance
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CustomerDetailScreen(
    customerId: Long,
    viewModel: CustomerDetailViewModel,
    onBack: () -> Unit,
    onAddCredit: () -> Unit,
    onRecordPayment: () -> Unit,
    onEditCustomer: () -> Unit,
    onMergeCustomer: () -> Unit,
    onShareStatement: () -> Unit,
    onDeleteCustomer: () -> Unit,
    onEditTransaction: (TransactionRow) -> Unit,
    onDeleteTransaction: (TransactionRow) -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(customerId) { viewModel.load(customerId) }

    FuturisticBackdrop {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading && state.detail == null -> CircularProgressIndicator(Modifier.padding(top = 64.dp))
                state.detail == null -> Column(
                    Modifier.fillMaxWidth().padding(20.dp).widthIn(max = 700.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    NeonPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(state.error ?: "Customer account unavailable.", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.load(customerId) }) { Text("Try again") }
                        }
                    }
                }
                else -> {
                    val detail = state.detail!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().widthIn(max = 780.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 34.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        item {
                            ScreenEntrance {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = onBack) { Text("Back") }
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Eyebrow("Customer profile")
                                        Text(
                                            detail.customer.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            detail.customer.phone.ifBlank { "Private khata account" },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    GlowPill("ACTIVE")
                                }
                            }
                        }
                        item {
                            ScreenEntrance(delayMillis = 70) {
                                NeonPanel(Modifier.fillMaxWidth(), radius = 25.dp) {
                                    Box(
                                        Modifier.fillMaxWidth().background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.surface,
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.66f)
                                                )
                                            )
                                        ).padding(19.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Eyebrow("Account balance")
                                            Text(
                                                if (detail.balance >= 0) "CUSTOMER OWES" else "CUSTOMER ADVANCE",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                letterSpacing = 1.1.sp
                                            )
                                            AnimatedValue(
                                                money(kotlin.math.abs(detail.balance)),
                                                color = MaterialTheme.colorScheme.onBackground,
                                                size = 30
                                            )
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                DetailTotal("TOTAL CREDIT", money(detail.totalCredit))
                                                DetailTotal("RECEIVED", money(detail.totalPayments), true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            ScreenEntrance(delayMillis = 130) {
                                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Eyebrow("Account controls")
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Button(
                                            onClick = onAddCredit,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(15.dp)
                                        ) { Text("+ Add sale") }
                                        OutlinedButton(
                                            onClick = onRecordPayment,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(15.dp)
                                        ) { Text("↓ Payment") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        OutlinedButton(onClick = onEditCustomer, modifier = Modifier.weight(1f)) { Text("Edit details") }
                                        OutlinedButton(onClick = onMergeCustomer, modifier = Modifier.weight(1f)) { Text("Merge duplicate") }
                                    }
                                    OutlinedButton(onClick = onShareStatement, modifier = Modifier.fillMaxWidth()) {
                                        Text("Share account statement")
                                    }
                                    OutlinedButton(
                                        onClick = onDeleteCustomer,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Delete this khata") }
                                }
                            }
                        }
                        item {
                            ScreenEntrance(delayMillis = 180) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Eyebrow("Ledger timeline")
                                        Text("Transaction history", style = MaterialTheme.typography.titleLarge)
                                    }
                                    GlowPill("${detail.transactions.size} ENTRIES")
                                }
                            }
                        }
                        if (detail.transactions.isEmpty()) {
                            item {
                                NeonPanel(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        Text("◷", color = MaterialTheme.colorScheme.primary, fontSize = 34.sp)
                                        Text("No activity yet", fontWeight = FontWeight.Bold)
                                        Text(
                                            "Record the first sale or payment to build this account's history.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(detail.transactions, key = { _, entry -> entry.id }) { index, entry ->
                                ScreenEntrance(delayMillis = (190 + index * 45).coerceAtMost(520)) {
                                    TransactionCard(
                                        entry = entry,
                                        onEdit = { onEditTransaction(entry) },
                                        onDelete = { onDeleteTransaction(entry) }
                                    )
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
private fun DetailTotal(label: String, value: String, end: Boolean = false) {
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 0.9.sp)
        Text(value, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun TransactionCard(
    entry: TransactionRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCredit = entry.type == "credit"
    NeonPanel(Modifier.fillMaxWidth(), radius = 18.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(
                            (if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.13f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isCredit) "+" else "↓", color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        if (isCredit) "CREDIT / SALE" else "PAYMENT RECEIVED",
                        color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Text(entry.date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Text(
                    (if (isCredit) "+" else "−") + money(entry.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            if (entry.description.isNotBlank()) {
                Text(entry.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit entry") }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
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
