package com.aikhata.app.ui.customer

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikhata.app.TransactionRow
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            state.loading && state.detail == null ->
                CircularProgressIndicator(Modifier.padding(top = 56.dp))
            state.detail == null -> Column(
                Modifier.fillMaxWidth().padding(20.dp).widthIn(max = 700.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Text(state.error ?: "Customer account unavailable.", color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.load(customerId) }) { Text("Try again") }
            }
            else -> {
                val detail = state.detail!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, top = 14.dp, end = 16.dp, bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = onBack) { Text("Back") }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    detail.customer.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    detail.customer.phone.ifBlank { "Customer khata" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    if (detail.balance >= 0) "Current amount due" else "Customer advance",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 13.sp
                                )
                                Text(
                                    money(kotlin.math.abs(detail.balance)),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 27.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Credit " + money(detail.totalCredit) + "  ·  Payments " + money(detail.totalPayments),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onAddCredit, modifier = Modifier.weight(1f)) {
                                    Text("Add sale")
                                }
                                OutlinedButton(onClick = onRecordPayment, modifier = Modifier.weight(1f)) {
                                    Text("Record payment")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onEditCustomer, modifier = Modifier.weight(1f)) {
                                    Text("Edit details")
                                }
                                OutlinedButton(onClick = onMergeCustomer, modifier = Modifier.weight(1f)) {
                                    Text("Merge duplicate")
                                }
                            }
                            OutlinedButton(onClick = onShareStatement, modifier = Modifier.fillMaxWidth()) {
                                Text("Share account statement")
                            }
                            OutlinedButton(
                                onClick = onDeleteCustomer,
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Delete this khata") }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Transaction history", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                detail.transactions.size.toString() + " entries",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (detail.transactions.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(
                                    "No entries yet. Add a sale or record a payment to start this khata.",
                                    Modifier.padding(18.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(detail.transactions, key = { it.id }) { entry ->
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

@Composable
private fun TransactionCard(
    entry: TransactionRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCredit = entry.type == "credit"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isCredit) "CREDIT / SALE" else "PAYMENT RECEIVED",
                    modifier = Modifier.weight(1f),
                    color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(money(entry.amount), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(entry.date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (entry.description.isNotBlank()) Text(entry.description, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Edit entry")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
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
