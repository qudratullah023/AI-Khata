package com.aikhata.app.ui.customers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun CustomersScreen(
    viewModel: CustomersViewModel,
    onBack: () -> Unit,
    onAddCustomer: () -> Unit,
    onCustomerClick: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).widthIn(max = 720.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Text("Customers", Modifier.weight(1f).padding(start = 12.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Button(onClick = onAddCustomer) { Text("Add") }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search name or phone") }
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::toggleSort, modifier = Modifier.weight(1f)) {
                    Text(if (state.sortByBalance) "Sort: highest due" else "Sort: recent/name")
                }
                OutlinedButton(onClick = viewModel::toggleDueOnly, modifier = Modifier.weight(1f)) {
                    Text(if (state.dueOnly) "Show all" else "Only money due")
                }
            }
            when {
                state.loading && state.customers.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.customers.isEmpty() -> Column(Modifier.padding(20.dp)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::refresh) { Text("Try again") }
                }
                state.customers.isEmpty() -> Text("No customers found. Add a customer to create the first khata.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(state.customers, key = { it.id }) { customer ->
                        AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 })) {
                            CustomerCard(customer) { onCustomerClick(customer.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(customer.phone.ifBlank { "Customer account" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(if (customer.balance >= 0) "Due" else "Advance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(kotlin.math.abs(customer.balance)), color = if (customer.balance > 0.009) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
