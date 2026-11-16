package com.aikhata.app.ui.customers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.aikhata.app.CustomerRow
import com.aikhata.app.ui.components.Eyebrow
import com.aikhata.app.ui.components.FuturisticBackdrop
import com.aikhata.app.ui.components.GlowPill
import com.aikhata.app.ui.components.NeonPanel
import com.aikhata.app.ui.components.ScreenEntrance
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel,
    onBack: () -> Unit,
    onAddCustomer: () -> Unit,
    onCustomerClick: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val totalDue = state.customers.filter { it.balance > 0.009 }.sumOf { it.balance }
    FuturisticBackdrop {
        Column(
            Modifier.fillMaxSize().widthIn(max = 780.dp).padding(horizontal = 16.dp)
        ) {
            ScreenEntrance {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Eyebrow("Client directory")
                        Text("Customers", style = MaterialTheme.typography.headlineMedium)
                    }
                    Button(onClick = onAddCustomer) { Text("+ Add") }
                }
            }
            ScreenEntrance(delayMillis = 60) {
                NeonPanel(Modifier.fillMaxWidth(), radius = 19.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("Filtered accounts")
                            Text(state.customers.size.toString(), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Visible customer accounts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Eyebrow("Total due")
                            Text(money(totalDue), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
            ScreenEntrance(delayMillis = 110) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Text("⌕", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary) },
                    label = { Text("Search customers") },
                    placeholder = { Text("Name or phone number") }
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.sortByBalance,
                        onClick = viewModel::toggleSort,
                        label = { Text(if (state.sortByBalance) "Highest due first" else "Sort by balance") }
                    )
                    FilterChip(
                        selected = state.dueOnly,
                        onClick = viewModel::toggleDueOnly,
                        label = { Text(if (state.dueOnly) "Only due" else "Show all") }
                    )
                }
            }

            when {
                state.loading && state.customers.isEmpty() -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null && state.customers.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::refresh) { Text("Try again") }
                }

                state.customers.isEmpty() -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    NeonPanel(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier.size(58.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center
                            ) { Text("◎", color = MaterialTheme.colorScheme.primary, fontSize = 29.sp) }
                            Text("No matching accounts", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (state.query.isNotBlank()) "Try another name or phone number."
                                else "Add your first customer and start a digital khata.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Button(onClick = onAddCustomer) { Text("Add customer") }
                        }
                    }
                }

                else -> LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp, top = 3.dp)
                ) {
                    itemsIndexed(state.customers, key = { _, customer -> customer.id }) { index, customer ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 })
                        ) {
                            CustomerCard(customer, index) { onCustomerClick(customer.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerRow, index: Int, onClick: () -> Unit) {
    NeonPanel(Modifier.fillMaxWidth(), radius = 18.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    customer.name.trim().take(1).uppercase(Locale.getDefault()),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(customer.phone.ifBlank { "Customer account" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(if (customer.balance >= 0) "DUE" else "ADVANCE", fontSize = 9.sp, letterSpacing = 0.9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    money(kotlin.math.abs(customer.balance)),
                    color = if (customer.balance > 0.009) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
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
