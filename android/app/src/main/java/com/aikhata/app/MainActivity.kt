package com.aikhata.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : Activity() {
    private lateinit var db: KhataDatabase
    private lateinit var root: LinearLayout
    private var unlocked = false
    private var pendingCameraUri: Uri? = null
    private var backAction: (() -> Unit)? = null
    private var currentScreen = "start"
    private var lastHomeBack = 0L
    private var sortCustomersByBalance = false

    private val green = Color.rgb(18, 107, 86)
    private val deepGreen = Color.rgb(19, 53, 44)
    private val ink = Color.rgb(32, 49, 42)
    private val muted = Color.rgb(111, 126, 117)
    private val paper = Color.rgb(245, 248, 245)
    private val line = Color.rgb(226, 234, 228)
    private val red = Color.rgb(190, 67, 67)
    private val prefs by lazy { getSharedPreferences("ai_khata_secure", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = KhataDatabase(this)
        window.statusBarColor = paper
        window.navigationBarColor = paper
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (db.isSetUp()) showUnlock() else showSetup()
    }

    @Deprecated("Back navigation is handled inside the app")
    override fun onBackPressed() {
        val action = backAction
        if (action != null) {
            backAction = null
            action.invoke()
            return
        }
        if (unlocked && currentScreen == "home") {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHomeBack < 1800) {
                super.onBackPressed()
            } else {
                lastHomeBack = now
                toast("Press back again to exit.")
            }
            return
        }
        if (!unlocked && db.isSetUp()) {
            finish()
            return
        }
        super.onBackPressed()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun money(value: Double) = "Rs. " + String.format(Locale.US, "%,.2f", value)
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun text(
        value: String,
        size: Int = 16,
        color: Int = ink,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
        includeFontPadding = false
    }

    private fun input(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setSingleLine(true)
        inputType = if (password) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        setTextColor(ink)
        setHintTextColor(muted)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), line)
        }
    }

    private fun actionButton(
        label: String,
        action: () -> Unit,
        primary: Boolean = false,
        danger: Boolean = false,
        height: Int = 50
    ) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setTextColor(if (primary) Color.WHITE else if (danger) red else green)
        isClickable = true
        isFocusable = true
        val fill = if (primary) green else if (danger) Color.rgb(255, 246, 245) else Color.WHITE
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            if (!primary) setStroke(dp(1), if (danger) Color.rgb(245, 218, 216) else line)
        }
        background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(34, 18, 107, 86)),
            shape,
            null
        )
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            animate().scaleX(0.97f).scaleY(0.97f).setDuration(65).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(105).withEndAction {
                    action()
                }.start()
            }.start()
        }
        minimumHeight = dp(height)
    }

    private fun button(
        label: String,
        action: () -> Unit,
        primary: Boolean = false,
        danger: Boolean = false
    ) {
        root.addView(
            actionButton(label, action, primary, danger),
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(9) }
        )
    }

    private fun gap(height: Int = 14) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun page(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
        backAction = back
        currentScreen = when (title) {
            "Customers" -> "customers"
            "Reports" -> "reports"
            "Settings" -> "settings"
            else -> if (title == db.shopName()) "home" else title.lowercase(Locale.US)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(10))
            addView(root)
        }
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (back != null) {
            val backView = TextView(this).apply {
                text = "‹"
                textSize = 34f
                gravity = Gravity.CENTER
                setTextColor(green)
                background = rounded(Color.WHITE, 13, line)
                setOnClickListener { back.invoke() }
            }
            header.addView(backView, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(11)
            })
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_khata_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            rightMargin = dp(10)
        })
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text(title, 22, deepGreen, true))
        if (!subtitle.isNullOrBlank()) titleBlock.addView(text(subtitle, 13, muted))
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        gap(17)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun card(title: String, value: String, detail: String = "") {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(Color.WHITE, 17, line)
        }
        box.addView(text(title, 13, muted, true))
        box.addView(text(value, 23, deepGreen, true))
        if (detail.isNotBlank()) box.addView(text(detail, 12, muted))
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun section(title: String, detail: String? = null) {
        root.addView(text(title, 17, deepGreen, true))
        if (!detail.isNullOrBlank()) root.addView(text(detail, 13, muted))
    }

    private fun showSetup() {
        unlocked = false
        page("Welcome to AI Khata", "A simple, private khata for your shop.")
        val shop = input("Shop name")
        val pin = input("Create a 6-digit PIN", true)
        val confirm = input("Confirm your PIN", true)
        root.addView(shop)
        gap(9)
        root.addView(pin)
        gap(9)
        root.addView(confirm)
        button("Create my shop ledger", {
            val enteredPin = pin.text.toString()
            if (shop.text.isNullOrBlank() || enteredPin.length < 6 ||
                enteredPin != confirm.text.toString()
            ) {
                toast("Enter a shop name and matching PIN with at least 6 digits.")
            } else {
                db.createOwner(shop.text.toString().trim(), enteredPin)
                unlocked = true
                showHome()
            }
        }, true)
    }

    private fun showUnlock() {
        unlocked = false
        page(db.shopName(), "Enter your PIN to continue.")
        val pin = input("Shop PIN", true)
        root.addView(pin)
        button("Unlock", {
            if (db.verifyPin(pin.text.toString())) {
                unlocked = true
                showHome()
            } else {
                toast("That PIN is not correct. Please try again.")
                pin.text.clear()
            }
        }, true)
    }

    private fun guard(): Boolean {
        if (!unlocked) {
            showUnlock()
            return false
        }
        return true
    }

    private fun showHome() {
        if (!guard()) return
        val totals = db.totals()
        val month = db.monthTotals(today().take(7))
        val customers = db.customers()
        page(db.shopName(), "Your shop at a glance.")
        card(
            "Total amount customers owe",
            money(totals.first - totals.second),
            "Sales on credit " + money(totals.first) + "  ·  Payments " + money(totals.second)
        )
        card(
            "This month",
            money(month.first) + " in sales",
            money(month.second) + " received in payments"
        )
        card("Customer accounts", customers.size.toString(), totals.third.toString() + " entries recorded")
        gap(10)
        button("Customers & khata books", { showCustomers() }, true)
        button("Add a new customer", { addCustomerDialog() })
        button("Monthly reports", { showReports() })
        button("Read a handwritten khata photo", { chooseImage() })
        button("Shop settings", { showSettings() })
        button("Lock the app", {
            unlocked = false
            showUnlock()
        }, danger = true)
        gap(12)
        section("Recently active")
        val recent = customers.sortedByDescending { it.id }.take(5)
        if (recent.isEmpty()) {
            root.addView(text("Your customer accounts will appear here.", 14, muted))
        } else {
            recent.forEach { customerTile(it) }
        }
    }

    private fun customerTile(customer: CustomerRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 15, line)
            isClickable = true
            isFocusable = true
            setOnClickListener { showCustomer(customer.id) }
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(customer.name, 16, ink, true))
        copy.addView(text(if (customer.phone.isBlank()) "Customer account" else customer.phone, 12, muted))
        box.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val amountText = if (customer.balance >= 0) {
            "Due\n" + money(customer.balance)
        } else {
            "Advance\n" + money(kotlin.math.abs(customer.balance))
        }
        box.addView(text(amountText, 13, if (customer.balance > 0) red else green, true))
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun showCustomers(query: String = "") {
        if (!guard()) return
        page("Customers", "Find a customer or open their khata.", back = { showHome() })
        val search = input("Search by name or phone").apply { setText(query) }
        root.addView(search)
        button("Search", { showCustomers(search.text.toString()) }, true)
        button("+ Add customer", { addCustomerDialog() })
        button(
            if (sortCustomersByBalance) "Sort: highest amount due first" else "Sort: customer name",
            {
                sortCustomersByBalance = !sortCustomersByBalance
                showCustomers(search.text.toString())
            }
        )
        val customers = db.customers(query).let {
            if (sortCustomersByBalance) it.sortedByDescending { customer -> customer.balance }
            else it
        }
        gap(6)
        if (customers.isEmpty()) {
            root.addView(text("No customers found. Add a customer to start a new khata.", 14, muted))
        } else {
            customers.forEach { customerTile(it) }
        }
    }

    private fun addCustomerDialog() {
        if (!guard()) return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = input("Customer name")
        val phone = input("Phone number (optional)").apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        box.addView(name)
        box.addView(phone, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("New customer")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                if (name.text.isNullOrBlank()) {
                    toast("Enter the customer's name.")
                } else {
                    val id = db.addCustomer(name.text.toString(), phone.text.toString())
                    dialog.dismiss()
                    showCustomer(id)
                }
            }
        }
        dialog.show()
    }

    private fun showCustomer(id: Long) {
        if (!guard()) return
        val customer = db.customer(id)
        if (customer == null) {
            showCustomers()
            return
        }
        val entries = db.transactions(id)
        val credits = entries.filter { it.type == "credit" }.sumOf { it.amount }
        val payments = entries.filter { it.type == "payment" }.sumOf { it.amount }
        page(customer.name, customer.phone.ifBlank { "Customer khata" }, back = { showCustomers() })
        card(
            "Current amount due",
            money(credits - payments),
            "Credit " + money(credits) + "  ·  Payments " + money(payments)
        )
        button("+ Add credit / sale", { addTransactionDialog(id, "credit") }, true)
        button("Record a payment", { addTransactionDialog(id, "payment") })
        button("Edit customer details", { editCustomerDialog(customer) })
        button("Share account statement", { shareStatement(id) })
        button("Delete this khata", { confirmDeleteCustomer(id) }, danger = true)
        gap(14)
        section("Transaction history", entries.size.toString() + " entries")
        if (entries.isEmpty()) {
            root.addView(text("No entries yet. Add a sale or record a payment.", 14, muted))
        } else {
            entries.forEach { transactionTile(id, it) }
        }
    }

    private fun transactionTile(customerId: Long, entry: TransactionRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(Color.WHITE, 15, line)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val kind = if (entry.type == "credit") "CREDIT / SALE" else "PAYMENT RECEIVED"
        top.addView(text(kind, 12, if (entry.type == "credit") red else green, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text(money(entry.amount), 15, ink, true))
        box.addView(top)
        box.addView(text(entry.date, 12, muted))
        if (entry.description.isNotBlank()) box.addView(text(entry.description, 14, ink))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val edit = actionButton("Edit entry", { editTransactionDialog(customerId, entry) }, height = 42)
        val delete = actionButton("Delete", { confirmDeleteTransaction(customerId, entry) }, danger = true, height = 42)
        actions.addView(edit, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(5) })
        actions.addView(delete, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
        box.addView(actions, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun editCustomerDialog(customer: CustomerRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = input("Customer name").apply { setText(customer.name) }
        val phone = input("Phone number").apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(customer.phone)
        }
        box.addView(name)
        box.addView(phone, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit customer")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                if (name.text.isNullOrBlank()) toast("Enter the customer's name.")
                else {
                    db.updateCustomer(customer.id, name.text.toString(), phone.text.toString())
                    dialog.dismiss()
                    showCustomer(customer.id)
                }
            }
        }
        dialog.show()
    }

    private fun addTransactionDialog(customerId: Long, type: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val amount = input("Amount in rupees").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val description = input("Description / item")
        val date = input("Date (YYYY-MM-DD)").apply { setText(today()) }
        box.addView(amount)
        box.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (type == "credit") "Add credit / sale" else "Record payment")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull()
                val day = date.text.toString().trim()
                if (value == null || value <= 0 || !value.isFinite() || !validDate(day)) {
                    toast("Enter a valid amount and date.")
                } else {
                    try {
                        db.addTransaction(customerId, type, value, description.text.toString(), day)
                        dialog.dismiss()
                        showCustomer(customerId)
                    } catch (error: Exception) {
                        toast("The entry could not be saved.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun editTransactionDialog(customerId: Long, entry: TransactionRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val amount = input("Amount in rupees").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(entry.amount.toString())
        }
        val description = input("Description / item").apply { setText(entry.description) }
        val date = input("Date (YYYY-MM-DD)").apply { setText(entry.date) }
        box.addView(amount)
        box.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit transaction")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull()
                val day = date.text.toString().trim()
                if (value == null || value <= 0 || !value.isFinite() || !validDate(day)) {
                    toast("Enter a valid amount and date.")
                } else {
                    db.updateTransaction(entry.id, value, description.text.toString(), day)
                    dialog.dismiss()
                    showCustomer(customerId)
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteTransaction(customerId: Long, entry: TransactionRow) {
        AlertDialog.Builder(this)
            .setTitle("Delete this entry?")
            .setMessage(
                "This will permanently remove the " + money(entry.amount) +
                    (if (entry.type == "credit") " credit / sale" else " payment") +
                    " entry from this customer's khata."
            )
            .setNegativeButton("Keep entry", null)
            .setPositiveButton("Delete") { _, _ ->
                db.deleteTransaction(entry.id)
                toast("Entry deleted.")
                showCustomer(customerId)
            }
            .show()
    }

    private fun confirmDeleteCustomer(customerId: Long) {
        val customer = db.customer(customerId) ?: return showCustomers()
        val count = db.transactions(customerId).size
        AlertDialog.Builder(this)
            .setTitle("Delete " + customer.name + "'s khata?")
            .setMessage(
                "This permanently deletes this customer and all " + count +
                    " transaction entries in their khata. This cannot be undone."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete khata") { _, _ ->
                db.deleteCustomer(customerId)
                toast("Customer khata deleted.")
                showCustomers()
            }
            .show()
    }

    private fun shareStatement(customerId: Long) {
        val customer = db.customer(customerId) ?: return
        val entries = db.transactions(customerId)
        val credits = entries.filter { it.type == "credit" }.sumOf { it.amount }
        val payments = entries.filter { it.type == "payment" }.sumOf { it.amount }
        val statement = buildString {
            append(db.shopName()).append("\n")
            append("Account statement: ").append(customer.name).append("\n")
            if (customer.phone.isNotBlank()) append("Phone: ").append(customer.phone).append("\n")
            append("------------------------------\n")
            entries.forEach {
                append(it.date).append(" | ")
                    .append(if (it.type == "credit") "Credit" else "Payment")
                    .append(" | ").append(money(it.amount))
                if (it.description.isNotBlank()) append(" | ").append(it.description)
                append("\n")
            }
            append("------------------------------\n")
            append("Total credit: ").append(money(credits)).append("\n")
            append("Total paid: ").append(money(payments)).append("\n")
            append("Balance due: ").append(money(credits - payments))
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Khata statement - " + customer.name)
            putExtra(Intent.EXTRA_TEXT, statement)
        }
        startActivity(Intent.createChooser(send, "Share account statement"))
    }

    private fun showReports(month: String = today().take(7)) {
        if (!guard()) return
        val totals = db.totals()
        val monthTotals = db.monthTotals(month)
        page("Reports", "A clear view of your shop's activity.", back = { showHome() })
        card("All-time credit / sales", money(totals.first))
        card("All-time payments received", money(totals.second))
        card("Total amount due", money(totals.first - totals.second))
        val monthField = input("Month (YYYY-MM)").apply { setText(month) }
        root.addView(text("Choose a month", 13, muted, true))
        root.addView(monthField)
        button("View month", {
            val selected = monthField.text.toString().trim()
            if (Regex("\\d{4}-(0[1-9]|1[0-2])").matches(selected)) showReports(selected)
            else toast("Enter the month like 2026-11.")
        }, true)
        gap(8)
        section("Activity for $month")
        card("Credit / sales", money(monthTotals.first))
        card("Payments received", money(monthTotals.second))
    }

    private fun showSettings() {
        if (!guard()) return
        page("Settings", "Manage your shop details and preferences.", back = { showHome() })
        val shop = input("Shop name").apply { setText(db.shopName()) }
        root.addView(text("Shop name", 13, muted, true))
        root.addView(shop)
        button("Save shop name", {
            if (shop.text.isNotBlank()) {
                db.updateShopName(shop.text.toString())
                toast("Shop name updated.")
                showSettings()
            } else toast("Enter a shop name.")
        }, true)

        gap(10)
        section("Change your PIN")
        val pin = input("New 6-digit PIN", true)
        val confirm = input("Confirm new PIN", true)
        root.addView(pin)
        root.addView(confirm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        button("Update PIN", {
            if (pin.text.length < 6 || pin.text.toString() != confirm.text.toString()) {
                toast("Your PINs must match and contain at least 6 digits.")
            } else {
                db.changePin(pin.text.toString())
                toast("Your PIN has been changed.")
                showSettings()
            }
        })

        gap(12)
        section("Handwritten photo reading", "Optional. Add an access key to read photos of ledger pages.")
        val api = input("AI access key")
        api.setText(if (hasApiKey()) "••••••••••••••••" else "")
        root.addView(api)
        button("Save access key", {
            val value = api.text.toString().trim()
            if (value.isNotBlank() && !value.startsWith("••")) {
                saveApiKey(value)
                toast("Photo reading is ready.")
                showSettings()
            } else {
                toast("Enter a new access key.")
            }
        })
        if (hasApiKey()) {
            button("Remove photo-reading access", {
                AlertDialog.Builder(this)
                    .setTitle("Remove access?")
                    .setMessage("Handwritten photo reading will be turned off until you add a key again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        deleteApiKey()
                        toast("Photo reading access removed.")
                        showSettings()
                    }.show()
            }, danger = true)
        }

        gap(12)
        section("Backup and restore", "Keep a copy of your shop records in a safe place.")
        button("Export a backup", { exportBackup() })
        button("Restore a backup", { importBackup() })
        gap(10)
        root.addView(text("AI Khata · Version 2.1.0", 12, muted))
    }

    private fun validDate(value: String): Boolean {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val parsed = formatter.parse(value) ?: return false
            formatter.format(parsed) == value
        } catch (_: Exception) {
            false
        }
    }

    private fun chooseImage() {
        if (!guard()) return
        if (!hasApiKey()) {
            AlertDialog.Builder(this)
                .setTitle("Set up photo reading")
                .setMessage("Add your AI access key in Settings before reading a handwritten khata photo.")
                .setPositiveButton("Open settings") { _, _ -> showSettings() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Choose a khata photo")
            .setItems(arrayOf("Take a new photo", "Choose from gallery")) { _, which ->
                if (which == 0) {
                    try {
                        val photo = File.createTempFile("khata-scan-", ".jpg", cacheDir)
                        pendingCameraUri = FileProvider.getUriForFile(
                            this, packageName + ".fileprovider", photo
                        )
                        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                        startActivityForResult(camera, 80)
                    } catch (error: Exception) {
                        toast("Could not open the camera.")
                    }
                } else {
                    startActivityForResult(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }, 77
                    )
                }
            }
            .show()
    }

    @Deprecated("Uses legacy result API for Android 10 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 80) {
            val uri = if (resultCode == RESULT_OK) pendingCameraUri else null
            pendingCameraUri = null
            if (uri != null) runAiExtraction(uri)
            return
        }
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            77 -> runAiExtraction(data.data!!)
            78 -> try {
                contentResolver.openOutputStream(data.data!!)?.use {
                    it.write(db.exportBackup().toByteArray(Charsets.UTF_8))
                }
                toast("Backup exported. Keep it somewhere safe.")
            } catch (error: Exception) {
                toast("Backup export failed.")
            }
            79 -> try {
                val json = contentResolver.openInputStream(data.data!!)!!.bufferedReader().use { it.readText() }
                val parsed = JSONObject(json)
                if (parsed.optString("format") != "ai-khata-backup") {
                    throw IllegalArgumentException("This is not an AI Khata backup.")
                }
                AlertDialog.Builder(this)
                    .setTitle("Restore this backup?")
                    .setMessage("Restoring will replace the customer accounts and entries currently in this app. Export a backup of your current records first if you need them.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore") { _, _ ->
                        try {
                            db.restoreBackup(json)
                            toast("Your backup has been restored.")
                            showHome()
                        } catch (error: Exception) {
                            toast("Backup could not be restored: " + error.message)
                        }
                    }
                    .show()
            } catch (error: Exception) {
                toast("Could not read this backup file.")
            }
        }
    }

    private fun runAiExtraction(uri: Uri) {
        val progress = ProgressDialog(this)
        progress.show()
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                require(bytes.size <= 10 * 1024 * 1024) { "Image is too large (10 MB maximum)." }
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                require(mime in listOf("image/jpeg", "image/png", "image/webp")) {
                    "Choose a JPG, PNG or WEBP image."
                }
                val prompt = "Read this handwritten shop ledger. Return ONLY JSON with entries array. Each entry: customer_name, date YYYY-MM-DD, description, type credit or payment, amount number. Do not guess unreadable amounts; omit uncertain rows."
                val parts = JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", mime)
                            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    ))
                val body = JSONObject().put(
                    "contents",
                    JSONArray().put(JSONObject().put("parts", parts))
                )
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=" +
                        apiKey()
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 25000
                connection.readTimeout = 45000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val response = (
                    if (connection.responseCode in 200..299) connection.inputStream
                    else connection.errorStream
                ).bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) {
                    throw Exception("Photo reading service returned error " + connection.responseCode + ".")
                }
                val answer = JSONObject(response)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
                val start = answer.indexOf('{')
                val end = answer.lastIndexOf('}')
                require(start >= 0 && end > start) { "No readable entries were returned." }
                val entries = JSONObject(answer.substring(start, end + 1))
                    .optJSONArray("entries") ?: JSONArray()
                runOnUiThread {
                    progress.dismiss()
                    reviewAiEntries(entries)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    toast("Photo reading failed: " + (error.message ?: "Check your connection and access key."))
                }
            }
        }.start()
    }

    private fun reviewAiEntries(entries: JSONArray) {
        val rows = ArrayList<JSONObject>()
        val lines = ArrayList<String>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val name = item.optString("customer_name").trim()
            val amount = item.optDouble("amount", Double.NaN)
            val date = item.optString("date", today())
            if (name.isBlank() || !amount.isFinite() || amount <= 0 || !validDate(date)) continue
            val type = if (item.optString("type") == "payment") "payment" else "credit"
            rows.add(
                JSONObject()
                    .put("name", name)
                    .put("amount", amount)
                    .put("type", type)
                    .put("date", date)
                    .put("description", item.optString("description", ""))
            )
            lines.add(
                name + ": " + (if (type == "credit") "Credit " else "Payment ") +
                    money(amount) + " · " + date
            )
        }
        if (rows.isEmpty()) {
            toast("No clear ledger entries were found.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Check " + rows.size + " suggested entries")
            .setMessage(
                lines.joinToString("\n\n") +
                    "\n\nPlease check every name, date and amount. Nothing is added until you confirm."
            )
            .setNegativeButton("Discard", null)
            .setPositiveButton("Save entries") { _, _ ->
                try {
                    rows.forEach { item ->
                        val name = item.getString("name")
                        val existing = db.customers().firstOrNull { it.name.equals(name, true) }
                        val id = existing?.id ?: db.addCustomer(name, "")
                        db.addTransaction(
                            id,
                            item.getString("type"),
                            item.getDouble("amount"),
                            item.getString("description"),
                            item.getString("date")
                        )
                    }
                    toast("Entries added to your khata.")
                    showHome()
                } catch (error: Exception) {
                    toast("Some entries could not be saved: " + error.message)
                }
            }
            .show()
    }

    private fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "my-shop-khata-backup.json")
        }
        startActivityForResult(intent, 78)
    }

    private fun importBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, 79)
    }

    private fun hasApiKey() = prefs.contains("gemini_key")

    private fun saveApiKey(value: String) {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray())
        prefs.edit()
            .putString("gemini_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("gemini_key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun apiKey(): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            encryptionKey(),
            GCMParameterSpec(
                128,
                Base64.decode(prefs.getString("gemini_iv", ""), Base64.NO_WRAP)
            )
        )
        return String(
            cipher.doFinal(
                Base64.decode(prefs.getString("gemini_key", ""), Base64.NO_WRAP)
            )
        )
    }

    private fun deleteApiKey() {
        prefs.edit().remove("gemini_iv").remove("gemini_key").apply()
    }

    private fun encryptionKey(): javax.crypto.SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "AIKhataGeminiKey"
        if (keyStore.containsAlias(alias)) {
            return (keyStore.getEntry(alias, null) as java.security.KeyStore.SecretKeyEntry).secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
