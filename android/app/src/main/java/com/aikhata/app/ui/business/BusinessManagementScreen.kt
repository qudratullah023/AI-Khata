package com.aikhata.app.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikhata.app.*
import com.aikhata.app.ui.components.Eyebrow
import com.aikhata.app.ui.components.FuturisticBackdrop
import com.aikhata.app.ui.components.GlowPill
import com.aikhata.app.ui.components.NeonPanel
import com.aikhata.app.ui.components.ScreenEntrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class BusinessData(
    val products: List<ProductRow>,
    val expenses: List<ExpenseRow>,
    val customers: List<CustomerRow>,
    val report: ProfitLossSnapshot
)

@Composable
fun BusinessManagementScreen(database: KhataDatabase, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(SimpleDateFormat("yyyy-MM", Locale.US).format(Date())) }
    var monthDraft by remember { mutableStateOf(month) }
    var products by remember { mutableStateOf(emptyList<ProductRow>()) }
    var expenses by remember { mutableStateOf(emptyList<ExpenseRow>()) }
    var customers by remember { mutableStateOf(emptyList<CustomerRow>()) }
    var report by remember { mutableStateOf<ProfitLossSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var productDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductRow?>(null) }
    var stockProduct by remember { mutableStateOf<ProductRow?>(null) }
    var saleProduct by remember { mutableStateOf<ProductRow?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductRow?>(null) }
    var expenseDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseRow?>(null) }
    var deletingExpense by remember { mutableStateOf<ExpenseRow?>(null) }

    LaunchedEffect(month, reloadKey) {
        loading = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                BusinessData(database.products(), database.expenses(), database.customers(), database.profitAndLoss(month))
            }
            products = loaded.products
            expenses = loaded.expenses
            customers = loaded.customers
            report = loaded.report
            message = null
        } catch (error: Exception) {
            message = error.message ?: "Could not load business records."
        } finally { loading = false }
    }

    fun saveWork(action: () -> Unit, success: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                message = success
                reloadKey++
            } catch (error: Exception) {
                message = error.message ?: "The change could not be saved."
            }
        }
    }

    FuturisticBackdrop {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 780.dp),
            contentPadding = PaddingValues(start = 17.dp, top = 17.dp, end = 17.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                ScreenEntrance {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onBack) { Text("Back") }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Eyebrow("Business operations")
                            Text("Inventory & expenses", style = MaterialTheme.typography.headlineMedium)
                        }
                        GlowPill("OFFLINE")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Inventory", "Expenses", "Profit & loss").forEachIndexed { index, title ->
                        if (selectedTab == index) {
                            Button(onClick = { selectedTab = index }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 9.dp)) { Text(title, fontSize = 11.sp) }
                        } else {
                            OutlinedButton(onClick = { selectedTab = index }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 9.dp)) { Text(title, fontSize = 11.sp) }
                        }
                    }
                }
            }
            if (message != null) item {
                NeonPanel(Modifier.fillMaxWidth()) {
                    Text(message.orEmpty(), Modifier.padding(13.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (loading && products.isEmpty() && expenses.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                } }
            } else when (selectedTab) {
                0 -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth(), radius = 20.dp) {
                            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Eyebrow("Stock overview")
                                    Text(products.size.toString() + " product(s)", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                                    Text((report?.lowStockCount ?: 0).toString() + " at or below low-stock level",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("STOCK VALUE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(money(report?.inventoryValue ?: 0.0), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    item { Button(onClick = { editingProduct = null; productDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add product") } }
                    if (products.isEmpty()) item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Your inventory is empty", fontWeight = FontWeight.SemiBold)
                                Text("Add products with purchase price, selling price and opening stock. Stock reduces automatically when you record a sale here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    } else items(products, key = { it.id }) { product ->
                        ProductCard(product, { saleProduct = product }, { editingProduct = product; productDialog = true },
                            { stockProduct = product }, { deletingProduct = product })
                    }
                }
                1 -> {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow("Operating costs")
                                Text("Shop expenses", style = MaterialTheme.typography.titleLarge)
                                Text("Record rent, electricity, transport, wages and other costs.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            GlowPill(expenses.size.toString() + " RECORDS")
                        }
                    }
                    item { Button(onClick = { editingExpense = null; expenseDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add expense") } }
                    if (expenses.isEmpty()) item {
                        NeonPanel(Modifier.fillMaxWidth()) { Text("No expenses recorded yet.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else items(expenses, key = { it.id }) { expense ->
                        ExpenseCard(expense, { editingExpense = expense; expenseDialog = true }, { deletingExpense = expense })
                    }
                }
                else -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth(), radius = 20.dp) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Eyebrow("Reporting period")
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = monthDraft, onValueChange = { monthDraft = it }, modifier = Modifier.weight(1f),
                                        singleLine = true, label = { Text("Month · YYYY-MM") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    Button(onClick = {
                                        if (Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(monthDraft)) month = monthDraft
                                        else message = "Enter a valid month in YYYY-MM format."
                                    }) { Text("Apply") }
                                }
                            }
                        }
                    }
                    report?.let { value ->
                        item {
                            ScreenEntrance(delayMillis = 60) {
                                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Text("Profit & loss · " + value.month, style = MaterialTheme.typography.titleLarge)
                                    SummaryMetric("Tracked product sales", value.trackedSales, "Sales recorded through inventory")
                                    SummaryMetric("Cost of goods sold", value.costOfGoodsSold, "Purchase cost saved at sale time")
                                    SummaryMetric("Gross profit", value.grossProfit, "Tracked sales minus their product costs", true)
                                    SummaryMetric("Operating expenses", value.expenses, value.expenseCount.toString() + " expense record(s)")
                                    SummaryMetric("Estimated net profit", value.netProfit, "Gross profit minus recorded expenses", true)
                                }
                            }
                        }
                        item {
                            NeonPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Eyebrow("Inventory health")
                                    KeyValueRow("Current stock value", money(value.inventoryValue))
                                    KeyValueRow("Products at low stock", value.lowStockCount.toString())
                                    KeyValueRow("Tracked product sales", value.trackedSaleCount.toString())
                                }
                            }
                        }
                        item {
                            NeonPanel(Modifier.fillMaxWidth()) {
                                Text(
                                    "Accounting note: this estimate includes only sales recorded from inventory products, using each product's purchase cost saved at the time of sale. Older/manual credit entries without a recorded product cost are excluded. Customer payments are collections of earlier sales, not new revenue.",
                                    Modifier.padding(15.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (productDialog) ProductEditorDialog(editingProduct, { productDialog = false }) { name, sku, unit, cost, price, stock, low ->
        val current = editingProduct
        saveWork({
            if (current == null) database.addProduct(name, sku, unit, cost, price, stock, low)
            else database.updateProduct(current.id, name, sku, unit, cost, price, low)
        }, if (current == null) "Product added." else "Product updated.")
        productDialog = false
    }
    stockProduct?.let { product -> StockDialog(product, { stockProduct = null }) { newStock ->
        saveWork({ database.setProductStock(product.id, newStock) }, "Stock updated.")
        stockProduct = null
    } }
    saleProduct?.let { product -> SaleDialog(product, customers, { saleProduct = null }) { customerId, qty, price, note, date ->
        saveWork({ database.recordProductSale(customerId, product.id, qty, price, note, date) }, "Sale saved and stock reduced.")
        saleProduct = null
    } }
    deletingProduct?.let { product ->
        ConfirmDialog("Delete " + product.name + "?", "Past sale records stay in the customer ledger. The product will be removed from inventory.",
            { deletingProduct = null }, {
                saveWork({ database.deleteProduct(product.id) }, "Product deleted.")
                deletingProduct = null
            })
    }
    if (expenseDialog) ExpenseEditorDialog(editingExpense, { expenseDialog = false }) { date, category, note, amount ->
        val current = editingExpense
        saveWork({
            if (current == null) database.addExpense(date, category, note, amount)
            else database.updateExpense(current.id, date, category, note, amount)
        }, if (current == null) "Expense saved." else "Expense updated.")
        expenseDialog = false
    }
    deletingExpense?.let { expense ->
        ConfirmDialog("Delete this expense?", expense.category + " · " + money(expense.amount),
            { deletingExpense = null }, {
                saveWork({ database.deleteExpense(expense.id) }, "Expense deleted.")
                deletingExpense = null
            })
    }
}

@Composable
private fun ProductCard(product: ProductRow, onSale: () -> Unit, onEdit: () -> Unit, onStock: () -> Unit, onDelete: () -> Unit) {
    NeonPanel(Modifier.fillMaxWidth(), radius = 18.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(listOf(product.sku.takeIf { it.isNotBlank() }?.let { "SKU " + it }, product.unit).filterNotNull().joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                if (product.stockQuantity <= product.lowStockThreshold) GlowPill("LOW STOCK")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("IN STOCK", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(quantity(product.stockQuantity) + " " + product.unit, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SELL PRICE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(product.sellingPrice), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("Purchase cost " + money(product.purchasePrice) + " · Low-stock alert at " + quantity(product.lowStockThreshold),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button(onClick = onSale, modifier = Modifier.weight(1f), enabled = product.stockQuantity > 0,
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 7.dp)) { Text("Record sale", fontSize = 10.sp) }
                OutlinedButton(onClick = onStock, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)) { Text("Stock", fontSize = 10.sp) }
                OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)) { Text("Edit", fontSize = 10.sp) }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 7.dp)) { Text("Delete", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun ExpenseCard(expense: ExpenseRow, onEdit: () -> Unit, onDelete: () -> Unit) {
    NeonPanel(Modifier.fillMaxWidth(), radius = 17.dp) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(expense.category, fontWeight = FontWeight.SemiBold)
                    Text(expense.date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Text(money(expense.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            if (expense.description.isNotBlank()) Text(expense.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun SummaryMetric(title: String, value: Double, detail: String, highlight: Boolean = false) {
    NeonPanel(Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Text(money(value), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ProductEditorDialog(
    product: ProductRow?, onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Double, Double, Double) -> Unit
) {
    var name by remember(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var sku by remember(product?.id) { mutableStateOf(product?.sku.orEmpty()) }
    var unit by remember(product?.id) { mutableStateOf(product?.unit ?: "pcs") }
    var cost by remember(product?.id) { mutableStateOf(product?.purchasePrice?.toString() ?: "0") }
    var price by remember(product?.id) { mutableStateOf(product?.sellingPrice?.toString() ?: "") }
    var stock by remember(product?.id) { mutableStateOf(product?.stockQuantity?.toString() ?: "0") }
    var low by remember(product?.id) { mutableStateOf(product?.lowStockThreshold?.toString() ?: "5") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add product" else "Edit product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Product name") }, singleLine = true)
                OutlinedTextField(sku, { sku = it }, label = { Text("SKU / code (optional)") }, singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit (pcs, kg, litre...)") }, singleLine = true)
                OutlinedTextField(cost, { cost = it }, label = { Text("Purchase price per unit (Rs.)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(price, { price = it }, label = { Text("Selling price per unit (Rs.)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (product == null) OutlinedTextField(stock, { stock = it }, label = { Text("Opening stock") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(low, { low = it }, label = { Text("Low-stock warning quantity") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            val c = cost.toDoubleOrNull()
            val p = price.toDoubleOrNull()
            val q = stock.toDoubleOrNull()
            val l = low.toDoubleOrNull()
            if (name.isBlank() || c == null || c < 0 || p == null || p <= 0 || q == null || q < 0 || l == null || l < 0) {
                error = "Check the product name, prices and quantities."
            } else onSave(name, sku, unit, c, p, q, l)
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StockDialog(product: ProductRow, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var stock by remember(product.id) { mutableStateOf(product.stockQuantity.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Update stock · " + product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Enter the new total quantity in stock.", fontSize = 12.sp)
                OutlinedTextField(stock, { stock = it }, label = { Text("Current stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val value = stock.toDoubleOrNull()
            if (value == null || value < 0 || !value.isFinite()) error = "Enter a valid quantity." else onSave(value)
        }) { Text("Save stock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SaleDialog(
    product: ProductRow, customers: List<CustomerRow>, onDismiss: () -> Unit,
    onSave: (Long, Double, Double, String, String) -> Unit
) {
    var customerId by remember(product.id) { mutableStateOf(customers.firstOrNull()?.id ?: -1L) }
    var customerMenu by remember { mutableStateOf(false) }
    var qtyText by remember(product.id) { mutableStateOf("1") }
    var priceText by remember(product.id) { mutableStateOf(product.sellingPrice.toString()) }
    var description by remember(product.id) { mutableStateOf(product.name) }
    var date by remember(product.id) { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedCustomer = customers.firstOrNull { it.id == customerId }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Record product sale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(product.name + " · available " + quantity(product.stockQuantity) + " " + product.unit, fontSize = 12.sp)
                Box {
                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedCustomer?.name ?: "Choose customer")
                    }
                    DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                        customers.forEach { customer ->
                            DropdownMenuItem(text = { Text(customer.name) }, onClick = { customerId = customer.id; customerMenu = false })
                        }
                    }
                }
                OutlinedTextField(qtyText, { qtyText = it }, label = { Text("Quantity sold") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(priceText, { priceText = it }, label = { Text("Selling price per " + product.unit + " (Rs.)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Sale date · YYYY-MM-DD") }, singleLine = true)
                Text("Total: " + money((qtyText.toDoubleOrNull() ?: 0.0) * (priceText.toDoubleOrNull() ?: 0.0)), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                if (customers.isEmpty()) Text("Add a customer before recording a sale.", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            val qty = qtyText.toDoubleOrNull()
            val price = priceText.toDoubleOrNull()
            if (selectedCustomer == null || qty == null || qty <= 0 || price == null || price <= 0 ||
                !qty.isFinite() || !price.isFinite() || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) {
                error = "Check the customer, quantity, price and date."
            } else if (qty > product.stockQuantity + 0.0000001) error = "Not enough stock is available."
            else onSave(customerId, qty, price, description, date)
        }, enabled = customers.isNotEmpty()) { Text("Save sale") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ExpenseEditorDialog(expense: ExpenseRow?, onDismiss: () -> Unit, onSave: (String, String, String, Double) -> Unit) {
    var date by remember(expense?.id) { mutableStateOf(expense?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var category by remember(expense?.id) { mutableStateOf(expense?.category.orEmpty()) }
    var description by remember(expense?.id) { mutableStateOf(expense?.description.orEmpty()) }
    var amount by remember(expense?.id) { mutableStateOf(expense?.amount?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (expense == null) "Add shop expense" else "Edit expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("Category (rent, electricity...)") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (Rs.)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(date, { date = it }, label = { Text("Date · YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Notes (optional)") }, singleLine = true)
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val value = amount.toDoubleOrNull()
            if (category.isBlank() || value == null || value <= 0 || !value.isFinite() ||
                !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) error = "Enter a category, valid amount and date."
            else onSave(date, category, description, value)
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ConfirmDialog(title: String, detail: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(detail) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun money(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "PK"))
    format.minimumFractionDigits = 2
    format.maximumFractionDigits = 2
    return "Rs. " + format.format(value)
}

private fun quantity(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
