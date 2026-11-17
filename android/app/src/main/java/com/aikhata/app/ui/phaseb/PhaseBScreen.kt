package com.aikhata.app.ui.phaseb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

private val phaseBTabs = listOf("Search", "Customers", "Suppliers", "Reminders", "Shop & access", "Wallet & print")

@Composable
fun PhaseBScreen(
    database: KhataDatabase,
    store: PhaseBStore,
    isStaff: Boolean,
    ownerPinMatches: (String) -> Boolean,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onShareStatement: (Long) -> Unit,
    onPrintStatement: (Long) -> Unit,
    onSwitchShop: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<LedgerSearchResult>()) }
    var searching by remember { mutableStateOf(false) }

    val customers = remember(database) { database.customers() }
    var selectedCustomerId by remember(customers) { mutableLongStateOf(customers.firstOrNull()?.id ?: -1L) }
    var customerMenu by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var shareOptIn by remember { mutableStateOf(false) }

    var suppliers by remember { mutableStateOf(store.suppliers()) }
    var selectedSupplierId by remember { mutableLongStateOf(suppliers.firstOrNull()?.id ?: -1L) }
    var supplierName by remember { mutableStateOf("") }
    var supplierPhone by remember { mutableStateOf("") }
    var supplierAmount by remember { mutableStateOf("") }
    var supplierDescription by remember { mutableStateOf("") }

    var weeklyEnabled by remember { mutableStateOf(store.getSetting("weekly_reminder_enabled", "1") == "1") }
    var weeklyDay by remember { mutableStateOf(store.getSetting("weekly_reminder_day", "1")) }
    var weeklyTime by remember { mutableStateOf(store.getSetting("weekly_reminder_time", "09:00")) }
    var dailyEnabled by remember { mutableStateOf(store.getSetting("daily_summary_enabled", "1") == "1") }
    var closingTime by remember { mutableStateOf(store.getSetting("daily_summary_time", "20:00")) }

    var staffPin by remember { mutableStateOf("") }
    var newShopName by remember { mutableStateOf("") }
    var newShopPin by remember { mutableStateOf("") }
    var newShopPinConfirm by remember { mutableStateOf("") }
    val shops = remember { ShopRegistry.shops(context) }
    val activeShopId = remember { ShopRegistry.activeShopId(context) }

    var jazzCash by remember { mutableStateOf(store.walletReferences().jazzCash) }
    var easypaisa by remember { mutableStateOf(store.walletReferences().easypaisa) }

    LaunchedEffect(selectedCustomerId) {
        if (selectedCustomerId > 0) {
            val meta = withContext(Dispatchers.IO) { store.customerMeta(selectedCustomerId) }
            tags = meta.tags
            notes = meta.notes
            shareOptIn = meta.shareOptIn
        }
    }

    fun reloadSuppliers() {
        suppliers = store.suppliers()
        if (suppliers.none { it.id == selectedSupplierId }) {
            selectedSupplierId = suppliers.firstOrNull()?.id ?: -1L
        }
    }

    fun saveCustomerProfile() {
        if (selectedCustomerId <= 0) {
            message = "Add a customer first."
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    store.saveCustomerMeta(CustomerMeta(selectedCustomerId, tags, notes, shareOptIn))
                }
            }.onSuccess { message = "Customer tags, notes and sharing preference saved." }
                .onFailure { message = it.message ?: "Could not save customer details." }
        }
    }

    FuturisticBackdrop {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 780.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenEntrance {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onBack) { Text("Back") }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Eyebrow("Phase B · Sticky features")
                            Text("Business tools", style = MaterialTheme.typography.headlineMedium)
                        }
                        GlowPill(if (isStaff) "STAFF" else "LOCAL")
                    }
                }
            }
            item {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    phaseBTabs.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }
            }
            if (message != null) item {
                NeonPanel(Modifier.fillMaxWidth()) {
                    Text(message.orEmpty(), Modifier.padding(13.dp), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
            when (selectedTab) {
                0 -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Eyebrow("Universal ledger search")
                                Text("Search customers, phone numbers, products, transaction descriptions, dates and amounts.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Name, phone, amount or date") }
                                )
                                Button(
                                    onClick = {
                                        searching = true
                                        scope.launch {
                                            searchResults = withContext(Dispatchers.IO) {
                                                (database.searchEverything(query) + store.searchSupplierEntries(query)).take(60)
                                            }
                                            searching = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = query.isNotBlank() && !searching
                                ) { Text(if (searching) "Searching…" else "Search everything") }
                                if (searchResults.isEmpty() && query.isNotBlank() && !searching) {
                                    Text("No matching records yet. Tap Search everything to search this shop.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    items(searchResults) { result ->
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().clickable(enabled = result.customerId != null) {
                                result.customerId?.let(onOpenCustomer)
                            }.padding(13.dp)) {
                                Eyebrow(result.kind.uppercase())
                                Text(result.title, fontWeight = FontWeight.SemiBold)
                                Text(result.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                if (result.customerId != null) Text("Open customer →", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Eyebrow("Customer profile details")
                                Text("Select a customer and keep useful shop notes and tags such as neighbour, relative or wholesale.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Box {
                                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = customers.isNotEmpty()) {
                                        Text(customers.firstOrNull { it.id == selectedCustomerId }?.let { it.name + " · " + it.phone } ?: "Choose customer")
                                    }
                                    DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                                        customers.forEach { customer ->
                                            DropdownMenuItem(
                                                text = { Text(customer.name + if (customer.phone.isNotBlank()) " · " + customer.phone else "") },
                                                onClick = { selectedCustomerId = customer.id; customerMenu = false }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(value = tags, onValueChange = { tags = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tags (comma separated)") }, placeholder = { Text("Neighbour, relative, wholesale") })
                                OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Private shop notes") }, minLines = 2)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Allow statement sharing", fontWeight = FontWeight.SemiBold)
                                        Text("Off by default. Turn on only when you choose to share a read-only PDF with this customer.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = shareOptIn, onCheckedChange = { shareOptIn = it })
                                }
                                Button(onClick = { saveCustomerProfile() }, modifier = Modifier.fillMaxWidth(), enabled = selectedCustomerId > 0) { Text("Save customer profile") }
                                OutlinedButton(
                                    onClick = { if (shareOptIn) onShareStatement(selectedCustomerId) else message = "Enable statement sharing for this customer first." },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = selectedCustomerId > 0 && shareOptIn
                                ) { Text("Create and share read-only PDF") }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Eyebrow("Supplier khata · Payables")
                                Text("Track amounts your shop owes to suppliers. A payable increases the balance; a payment reduces it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                OutlinedTextField(value = supplierName, onValueChange = { supplierName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Supplier name") }, singleLine = true)
                                OutlinedTextField(value = supplierPhone, onValueChange = { supplierPhone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Phone (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { store.addSupplier(supplierName, supplierPhone) } }
                                                .onSuccess { supplierName = ""; supplierPhone = ""; reloadSuppliers(); message = "Supplier added." }
                                                .onFailure { message = it.message ?: "Could not add supplier." }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = supplierName.isNotBlank()
                                ) { Text("+ Add supplier") }
                            }
                        }
                    }
                    items(suppliers, key = { it.id }) { supplier ->
                        val selected = supplier.id == selectedSupplierId
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().clickable { selectedSupplierId = supplier.id }.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(supplier.name, fontWeight = FontWeight.SemiBold)
                                        Text(supplier.phone.ifBlank { "Supplier" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("Rs. " + String.format(java.util.Locale.US, "%,.2f", supplier.balance), color = if (supplier.balance > 0.009) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                if (selected) {
                                    OutlinedTextField(value = supplierAmount, onValueChange = { supplierAmount = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount in Rs.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                    OutlinedTextField(value = supplierDescription, onValueChange = { supplierDescription = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description (optional)") }, singleLine = true)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            saveSupplierEntry(store, supplier.id, "payable", supplierAmount, supplierDescription, scope, { reloadSuppliers(); supplierAmount = ""; supplierDescription = ""; message = "Supplier payable recorded." }, { message = it })
                                        }, modifier = Modifier.weight(1f), enabled = supplierAmount.toDoubleOrNull()?.let { it > 0 } == true) { Text("Add payable") }
                                        OutlinedButton(onClick = {
                                            saveSupplierEntry(store, supplier.id, "payment", supplierAmount, supplierDescription, scope, { reloadSuppliers(); supplierAmount = ""; supplierDescription = ""; message = "Supplier payment recorded." }, { message = it })
                                        }, modifier = Modifier.weight(1f), enabled = supplierAmount.toDoubleOrNull()?.let { it > 0 } == true) { Text("Record payment") }
                                    }
                                    val entries = store.supplierEntries(supplier.id)
                                    entries.forEach { entry ->
                                        Text("${entry.date} · ${if (entry.type == "payable") "Payable" else "Payment"} · Rs. ${String.format(java.util.Locale.US, "%,.2f", entry.amount)}${if (entry.description.isBlank()) "" else " · " + entry.description}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (suppliers.isEmpty()) item { Text("No suppliers added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                3 -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Eyebrow("Reminder schedule")
                                Text("Notifications are generated on this phone. Core bookkeeping stays offline.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Weekly overdue reminder", fontWeight = FontWeight.SemiBold)
                                        Text("Counts overdue customers and supplier balances.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    }
                                    Switch(checked = weeklyEnabled, onCheckedChange = { weeklyEnabled = it })
                                }
                                OutlinedTextField(value = weeklyDay, onValueChange = { weeklyDay = it.filter(Char::isDigit).take(1) }, modifier = Modifier.fillMaxWidth(), label = { Text("Day of week (1 Monday – 7 Sunday)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                OutlinedTextField(value = weeklyTime, onValueChange = { weeklyTime = it.take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("Weekly reminder time (HH:mm)") }, singleLine = true)
                                Divider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Daily closing summary", fontWeight = FontWeight.SemiBold)
                                        Text("Sales, payments received and new credit for the day.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    }
                                    Switch(checked = dailyEnabled, onCheckedChange = { dailyEnabled = it })
                                }
                                OutlinedTextField(value = closingTime, onValueChange = { closingTime = it.take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("Daily closing time (HH:mm)") }, singleLine = true)
                                Button(onClick = {
                                    if (weeklyDay.toIntOrNull()?.let { it in 1..7 } != true || !validTime(weeklyTime) || !validTime(closingTime)) {
                                        message = "Enter a day from 1 to 7 and times in 24-hour HH:mm format."
                                    } else {
                                        store.putSetting("weekly_reminder_enabled", if (weeklyEnabled) "1" else "0")
                                        store.putSetting("weekly_reminder_day", weeklyDay)
                                        store.putSetting("weekly_reminder_time", weeklyTime)
                                        store.putSetting("daily_summary_enabled", if (dailyEnabled) "1" else "0")
                                        store.putSetting("daily_summary_time", closingTime)
                                        schedulePhaseBNotifications(context, store)
                                        message = "Reminder schedule saved."
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Save reminder schedule") }
                                Text("Android may deliver background notifications a little later to save battery.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Eyebrow("Multi-shop workspace")
                                Text("Each shop has a separate local database and its own PIN.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                shops.forEach { shop ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(shop.name, fontWeight = FontWeight.SemiBold)
                                            Text(if (shop.id == activeShopId) "Currently active" else "Separate shop ledger", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (shop.id != activeShopId) {
                                            OutlinedButton(onClick = { onSwitchShop(shop.id) }) { Text("Switch") }
                                        } else GlowPill("ACTIVE")
                                    }
                                }
                                Divider()
                                Text("Create another shop", fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(value = newShopName, onValueChange = { newShopName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New shop name") }, singleLine = true)
                                OutlinedTextField(value = newShopPin, onValueChange = { newShopPin = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(), label = { Text("New shop PIN (6+ digits)") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                OutlinedTextField(value = newShopPinConfirm, onValueChange = { newShopPinConfirm = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm new shop PIN") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                Button(onClick = {
                                    runCatching { ShopRegistry.createShop(context, newShopName, newShopPin).also { newShopName = ""; newShopPin = ""; newShopPinConfirm = "" } }
                                        .onSuccess { onSwitchShop(it.id) }
                                        .onFailure { message = it.message ?: "Could not create this shop." }
                                }, modifier = Modifier.fillMaxWidth(), enabled = newShopName.isNotBlank() && newShopPin.length >= 6 && newShopPin == newShopPinConfirm) { Text("Create and open shop") }
                            }
                        }
                    }
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Eyebrow("Staff access")
                                Text("Set a separate staff PIN. Staff can add customer sales but cannot delete records or view profit and loss.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                OutlinedTextField(value = staffPin, onValueChange = { staffPin = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(), label = { Text("New staff PIN (6+ digits)") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                Button(onClick = {
                                    runCatching {
                                        require(!ownerPinMatches(staffPin)) { "The staff PIN must be different from the owner PIN." }
                                        store.setStaffPin(staffPin)
                                        staffPin = ""
                                        message = "Staff PIN saved. At the unlock screen, staff can enter this PIN."
                                    }.onFailure { message = it.message ?: "Could not save staff PIN." }
                                }, modifier = Modifier.fillMaxWidth(), enabled = staffPin.length >= 6 && !isStaff) { Text(if (store.hasStaffPin()) "Update staff PIN" else "Set staff PIN") }
                                if (store.hasStaffPin() && !isStaff) {
                                    OutlinedButton(onClick = { store.clearStaffPin(); message = "Staff PIN removed." }, modifier = Modifier.fillMaxWidth()) { Text("Remove staff PIN") }
                                }
                            }
                        }
                    }
                }
                5 -> {
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Eyebrow("Local wallet references")
                                Text("Store wallet numbers for display on customer statements. This does not process payments.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                OutlinedTextField(value = jazzCash, onValueChange = { jazzCash = it.take(40) }, modifier = Modifier.fillMaxWidth(), label = { Text("JazzCash number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                                OutlinedTextField(value = easypaisa, onValueChange = { easypaisa = it.take(40) }, modifier = Modifier.fillMaxWidth(), label = { Text("Easypaisa number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                                Button(onClick = {
                                    store.saveWalletReferences(WalletReferences(jazzCash, easypaisa))
                                    message = "Wallet references saved. They will appear on opted-in PDF statements."
                                }, modifier = Modifier.fillMaxWidth()) { Text("Save wallet references") }
                            }
                        }
                    }
                    item {
                        NeonPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Eyebrow("Bluetooth thermal printing")
                                Text("Choose a customer statement and send a simple receipt to a paired Bluetooth ESC/POS printer.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Box {
                                    var printerCustomerMenu by remember { mutableStateOf(false) }
                                    OutlinedButton(onClick = { printerCustomerMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = customers.isNotEmpty()) {
                                        Text(customers.firstOrNull { it.id == selectedCustomerId }?.name ?: "Choose customer")
                                    }
                                    DropdownMenu(expanded = printerCustomerMenu, onDismissRequest = { printerCustomerMenu = false }) {
                                        customers.forEach { c ->
                                            DropdownMenuItem(text = { Text(c.name) }, onClick = { selectedCustomerId = c.id; printerCustomerMenu = false })
                                        }
                                    }
                                }
                                OutlinedButton(onClick = { if (selectedCustomerId > 0) onPrintStatement(selectedCustomerId) }, modifier = Modifier.fillMaxWidth(), enabled = selectedCustomerId > 0) {
                                    Text("Print by Bluetooth")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun saveSupplierEntry(
    store: PhaseBStore,
    supplierId: Long,
    type: String,
    rawAmount: String,
    description: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val amount = rawAmount.toDoubleOrNull()
    if (amount == null || !amount.isFinite() || amount <= 0.0) {
        onError("Enter a valid amount greater than zero.")
        return
    }
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { store.addSupplierEntry(supplierId, type, amount, description) } }
            .onSuccess { onSuccess() }
            .onFailure { onError(it.message ?: "Could not save supplier entry.") }
    }
}

private fun validTime(value: String): Boolean =
    Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
