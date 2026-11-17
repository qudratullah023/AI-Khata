package com.aikhata.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Locale

data class CustomerRow(val id: Long, val name: String, val phone: String, val balance: Double)
data class TransactionRow(
    val id: Long, val date: String, val type: String, val description: String, val amount: Double,
    val productId: Long? = null, val quantity: Double? = null, val unitCost: Double? = null
)
data class ProductRow(
    val id: Long, val name: String, val sku: String, val unit: String, val purchasePrice: Double,
    val sellingPrice: Double, val stockQuantity: Double, val lowStockThreshold: Double
)
data class ExpenseRow(val id: Long, val date: String, val category: String, val description: String, val amount: Double)
data class LedgerSearchResult(val kind: String, val title: String, val detail: String, val customerId: Long? = null)
data class ProfitLossSnapshot(
    val month: String, val trackedSales: Double, val costOfGoodsSold: Double, val grossProfit: Double,
    val expenses: Double, val netProfit: Double, val expenseCount: Int, val inventoryValue: Double,
    val lowStockCount: Int, val trackedSaleCount: Int
)

class KhataDatabase(context: Context, databaseName: String = "ai_khata_local.db") : SQLiteOpenHelper(context, databaseName, null, 3) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE owner (id INTEGER PRIMARY KEY CHECK(id=1), shop_name TEXT NOT NULL, pin_salt TEXT NOT NULL, pin_hash TEXT NOT NULL, created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, sku TEXT NOT NULL DEFAULT '', unit TEXT NOT NULL DEFAULT 'pcs', purchase_price_paisa INTEGER NOT NULL CHECK(purchase_price_paisa >= 0), selling_price_paisa INTEGER NOT NULL CHECK(selling_price_paisa > 0), stock_milli INTEGER NOT NULL CHECK(stock_milli >= 0), low_stock_milli INTEGER NOT NULL DEFAULT 5000 CHECK(low_stock_milli >= 0), created_at TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_products_name ON products(name COLLATE NOCASE)")
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE, transaction_date TEXT NOT NULL, transaction_type TEXT NOT NULL CHECK(transaction_type IN ('credit','payment')), description TEXT NOT NULL DEFAULT '', amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL, product_id INTEGER REFERENCES products(id) ON DELETE SET NULL, quantity_milli INTEGER, unit_cost_paisa INTEGER)")
        db.execSQL("CREATE INDEX idx_local_tx_customer_date ON transactions(customer_id, transaction_date)")
        db.execSQL("CREATE INDEX idx_product_sales_date ON transactions(transaction_date, quantity_milli)")
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, expense_date TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_expenses_date ON expenses(expense_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Convert stored rupees to integer paisa while preserving record IDs.
            db.execSQL("CREATE TABLE transactions_new (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE, transaction_date TEXT NOT NULL, transaction_type TEXT NOT NULL CHECK(transaction_type IN ('credit','payment')), description TEXT NOT NULL DEFAULT '', amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL)")
            db.execSQL("INSERT INTO transactions_new (id, customer_id, transaction_date, transaction_type, description, amount_paisa, created_at) SELECT id, customer_id, transaction_date, transaction_type, description, MAX(1, CAST(ROUND(amount * 100.0 + 0.000001) AS INTEGER)), created_at FROM transactions WHERE amount > 0")
            db.execSQL("DROP TABLE transactions")
            db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            db.execSQL("CREATE INDEX idx_local_tx_customer_date ON transactions(customer_id, transaction_date)")
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, sku TEXT NOT NULL DEFAULT '', unit TEXT NOT NULL DEFAULT 'pcs', purchase_price_paisa INTEGER NOT NULL CHECK(purchase_price_paisa >= 0), selling_price_paisa INTEGER NOT NULL CHECK(selling_price_paisa > 0), stock_milli INTEGER NOT NULL CHECK(stock_milli >= 0), low_stock_milli INTEGER NOT NULL DEFAULT 5000 CHECK(low_stock_milli >= 0), created_at TEXT NOT NULL)")
            db.execSQL("CREATE INDEX idx_products_name ON products(name COLLATE NOCASE)")
            db.execSQL("ALTER TABLE transactions ADD COLUMN product_id INTEGER REFERENCES products(id) ON DELETE SET NULL")
            db.execSQL("ALTER TABLE transactions ADD COLUMN quantity_milli INTEGER")
            db.execSQL("ALTER TABLE transactions ADD COLUMN unit_cost_paisa INTEGER")
            db.execSQL("CREATE INDEX idx_product_sales_date ON transactions(transaction_date, quantity_milli)")
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, expense_date TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL)")
            db.execSQL("CREATE INDEX idx_expenses_date ON expenses(expense_date)")
        }
    }

    /**
     * Read-only SQLite health check. Run this on a background thread because quick_check
     * scans database pages. It never attempts to alter, rebuild, or delete user records.
     */
    fun checkIntegrity(): Boolean {
        val db = readableDatabase
        val quickCheckPassed = db.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
        }
        if (!quickCheckPassed) return false
        return db.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            !cursor.moveToFirst()
        }
    }

    fun <T> inTransaction(operation: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = operation()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    fun isSetUp() = readableDatabase.rawQuery("SELECT 1 FROM owner WHERE id=1", null).use { it.moveToFirst() }

    fun createOwner(shop: String, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("id", 1)
            put("shop_name", shop)
            put("pin_salt", salt.hex())
            put("pin_hash", derive(pin, salt).hex())
            put("created_at", now())
        }
        writableDatabase.insertOrThrow("owner", null, values)
    }

    fun verifyPin(pin: String): Boolean = readableDatabase.rawQuery(
        "SELECT pin_salt,pin_hash FROM owner WHERE id=1", null
    ).use {
        if (!it.moveToFirst()) false
        else MessageDigest.isEqual(it.getString(1).unhex(), derive(pin, it.getString(0).unhex()))
    }

    fun changePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        writableDatabase.execSQL(
            "UPDATE owner SET pin_salt=?,pin_hash=? WHERE id=1",
            arrayOf(salt.hex(), derive(pin, salt).hex())
        )
    }

    fun shopName() = readableDatabase.rawQuery("SELECT shop_name FROM owner WHERE id=1", null).use {
        if (it.moveToFirst()) it.getString(0) else "My shop"
    }

    fun updateShopName(name: String) {
        writableDatabase.execSQL("UPDATE owner SET shop_name=? WHERE id=1", arrayOf(name.trim()))
    }

    fun addCustomer(name: String, phone: String): Long {
        require(name.isNotBlank()) { "Customer name is required." }
        val values = ContentValues().apply {
            put("name", name.trim())
            put("phone", phone.trim())
            put("created_at", now())
        }
        return writableDatabase.insertOrThrow("customers", null, values)
    }

    fun updateCustomer(id: Long, name: String, phone: String) {
        require(name.isNotBlank()) { "Customer name is required." }
        val values = ContentValues().apply {
            put("name", name.trim())
            put("phone", phone.trim())
        }
        writableDatabase.update("customers", values, "id=?", arrayOf(id.toString()))
    }

    fun deleteCustomer(id: Long): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val productQuantities = db.rawQuery(
                "SELECT product_id,COALESCE(SUM(quantity_milli),0) FROM transactions WHERE customer_id=? AND product_id IS NOT NULL AND quantity_milli IS NOT NULL GROUP BY product_id",
                arrayOf(id.toString())
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1))
                }
            }
            val deleted = db.delete("customers", "id=?", arrayOf(id.toString()))
            if (deleted > 0) {
                productQuantities.forEach { (productId, quantityMilli) ->
                    db.execSQL(
                        "UPDATE products SET stock_milli=stock_milli+? WHERE id=?",
                        arrayOf(quantityMilli, productId)
                    )
                }
            }
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    fun mergeCustomers(sourceId: Long, targetId: Long) {
        require(sourceId != targetId) { "Choose two different customers." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sourceExists = db.rawQuery(
                "SELECT 1 FROM customers WHERE id=?", arrayOf(sourceId.toString())
            ).use { it.moveToFirst() }
            val targetExists = db.rawQuery(
                "SELECT 1 FROM customers WHERE id=?", arrayOf(targetId.toString())
            ).use { it.moveToFirst() }
            require(sourceExists && targetExists) { "One of these customer accounts no longer exists." }
            db.update("transactions", ContentValues().apply {
                put("customer_id", targetId)
            }, "customer_id=?", arrayOf(sourceId.toString()))
            db.delete("customers", "id=?", arrayOf(sourceId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun customers(query: String = ""): List<CustomerRow> =
        readableDatabase.rawQuery(
            """SELECT c.id,c.name,c.phone,
            COALESCE(SUM(CASE WHEN t.transaction_type='credit' THEN t.amount_paisa ELSE 0 END),0),
            COALESCE(SUM(CASE WHEN t.transaction_type='payment' THEN t.amount_paisa ELSE 0 END),0)
            FROM customers c LEFT JOIN transactions t ON t.customer_id=c.id
            WHERE c.name LIKE ? OR c.phone LIKE ?
            GROUP BY c.id ORDER BY c.name COLLATE NOCASE""",
            arrayOf("%$query%", "%$query%")
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val balancePaisa = LedgerMath.calculateBalance(cursor.getLong(3), cursor.getLong(4))
                    add(CustomerRow(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        balancePaisa / 100.0
                    ))
                }
            }
        }

    fun customer(id: Long) = customers().firstOrNull { it.id == id }

    fun addTransaction(
        id: Long,
        type: String,
        amount: Double,
        description: String,
        date: String,
        productId: Long? = null,
        quantity: Double? = null,
        unitCost: Double? = null
    ) {
        require(type == "credit" || type == "payment")
        require(amount > 0 && amount.isFinite())
        require(LedgerMath.isValidDate(date))
        val hasSaleSnapshot = productId != null || quantity != null || unitCost != null
        require(!hasSaleSnapshot || (type == "credit" && quantity != null && unitCost != null &&
            unitCost >= 0.0 && unitCost.isFinite())) {
            "The product sale details are incomplete."
        }
        val quantityMilli = if (hasSaleSnapshot) toMilli(quantity!!, allowZero = false) else null
        val unitCostPaisa = if (hasSaleSnapshot) toPaisaNonNegative(unitCost!!) else null

        val db = writableDatabase
        if (productId == null) {
            require(!hasSaleSnapshot) {
                "Product sale details require a valid product."
            }
            val values = ContentValues().apply {
                put("customer_id", id)
                put("transaction_date", date)
                put("transaction_type", type)
                put("description", description.trim())
                put("amount_paisa", toPaisa(amount))
                put("created_at", now())
                if (quantityMilli != null && unitCostPaisa != null) {
                    put("quantity_milli", quantityMilli)
                    put("unit_cost_paisa", unitCostPaisa)
                }
            }
            db.insertOrThrow("transactions", null, values)
            return
        }

        require(quantityMilli != null && unitCostPaisa != null) { "The product sale details are incomplete." }
        db.beginTransaction()
        try {
            val stock = db.rawQuery(
                "SELECT stock_milli FROM products WHERE id=?",
                arrayOf(productId.toString())
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
                ?: throw IllegalArgumentException("The original product no longer exists.")
            require(stock >= quantityMilli) { "Not enough stock to restore this sale." }
            val values = ContentValues().apply {
                put("customer_id", id)
                put("transaction_date", date)
                put("transaction_type", type)
                put("description", description.trim())
                put("amount_paisa", toPaisa(amount))
                put("created_at", now())
                put("product_id", productId)
                put("quantity_milli", quantityMilli)
                put("unit_cost_paisa", unitCostPaisa)
            }
            db.insertOrThrow("transactions", null, values)
            db.update(
                "products",
                ContentValues().apply { put("stock_milli", stock - quantityMilli) },
                "id=?",
                arrayOf(productId.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    fun updateTransaction(id: Long, amount: Double, description: String, date: String) {
        require(amount > 0 && amount.isFinite())
        require(LedgerMath.isValidDate(date)) { "Enter a valid transaction date." }
        val values = ContentValues().apply {
            put("amount_paisa", toPaisa(amount))
            put("description", description.trim())
            put("transaction_date", date)
        }
        writableDatabase.update("transactions", values, "id=?", arrayOf(id.toString()))
    }

    fun deleteTransaction(id: Long): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sale = db.rawQuery(
                "SELECT product_id,quantity_milli FROM transactions WHERE id=?",
                arrayOf(id.toString())
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0) && !cursor.isNull(1)) {
                    cursor.getLong(0) to cursor.getLong(1)
                } else null
            }
            val deleted = db.delete("transactions", "id=?", arrayOf(id.toString()))
            if (deleted > 0 && sale != null) {
                db.execSQL(
                    "UPDATE products SET stock_milli=stock_milli+? WHERE id=?",
                    arrayOf(sale.second, sale.first)
                )
            }
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    fun transactions(id: Long): List<TransactionRow> =
        readableDatabase.rawQuery(
            "SELECT id,transaction_date,transaction_type,description,amount_paisa,product_id,quantity_milli,unit_cost_paisa FROM transactions WHERE customer_id=? ORDER BY transaction_date DESC,id DESC",
            arrayOf(id.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(TransactionRow(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getLong(4) / 100.0,
                        if (cursor.isNull(5)) null else cursor.getLong(5),
                        if (cursor.isNull(6)) null else cursor.getLong(6) / 1000.0,
                        if (cursor.isNull(7)) null else cursor.getLong(7) / 100.0
                    ))
                }
            }
        }

    fun products(): List<ProductRow> =
        readableDatabase.rawQuery("SELECT id,name,sku,unit,purchase_price_paisa,selling_price_paisa,stock_milli,low_stock_milli FROM products ORDER BY name COLLATE NOCASE", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(ProductRow(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.getLong(4) / 100.0, cursor.getLong(5) / 100.0,
                    cursor.getLong(6) / 1000.0, cursor.getLong(7) / 1000.0
                ))
            }
        }

    fun addProduct(name: String, sku: String, unit: String, purchasePrice: Double, sellingPrice: Double, stockQuantity: Double, lowStockThreshold: Double): Long {
        require(name.isNotBlank()) { "Product name is required." }
        require(purchasePrice >= 0.0 && purchasePrice.isFinite())
        require(sellingPrice > 0.0 && sellingPrice.isFinite())
        val values = ContentValues().apply {
            put("name", name.trim().take(100)); put("sku", sku.trim().take(50))
            put("unit", unit.trim().ifBlank { "pcs" }.take(20))
            put("purchase_price_paisa", toPaisaNonNegative(purchasePrice))
            put("selling_price_paisa", toPaisa(sellingPrice))
            put("stock_milli", toMilli(stockQuantity)); put("low_stock_milli", toMilli(lowStockThreshold))
            put("created_at", now())
        }
        return writableDatabase.insertOrThrow("products", null, values)
    }

    fun updateProduct(id: Long, name: String, sku: String, unit: String, purchasePrice: Double, sellingPrice: Double, lowStockThreshold: Double) {
        require(name.isNotBlank() && purchasePrice >= 0.0 && purchasePrice.isFinite() && sellingPrice > 0.0 && sellingPrice.isFinite())
        val values = ContentValues().apply {
            put("name", name.trim().take(100)); put("sku", sku.trim().take(50))
            put("unit", unit.trim().ifBlank { "pcs" }.take(20))
            put("purchase_price_paisa", toPaisaNonNegative(purchasePrice))
            put("selling_price_paisa", toPaisa(sellingPrice)); put("low_stock_milli", toMilli(lowStockThreshold))
        }
        require(writableDatabase.update("products", values, "id=?", arrayOf(id.toString())) == 1) { "Product not found." }
    }

    fun setProductStock(id: Long, quantity: Double) {
        require(writableDatabase.update("products", ContentValues().apply { put("stock_milli", toMilli(quantity)) }, "id=?", arrayOf(id.toString())) == 1) { "Product not found." }
    }

    fun deleteProduct(id: Long): Int = writableDatabase.delete("products", "id=?", arrayOf(id.toString()))

    fun recordProductSale(customerId: Long, productId: Long, quantity: Double, unitPrice: Double, description: String, date: String) {
        require(LedgerMath.isValidDate(date)) { "Enter a valid sale date." }
        val qtyMilli = toMilli(quantity, allowZero = false)
        require(unitPrice > 0.0 && unitPrice.isFinite()) { "Enter a valid selling price." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val product = db.rawQuery("SELECT name,stock_milli,purchase_price_paisa FROM products WHERE id=?", arrayOf(productId.toString())).use { c ->
                if (!c.moveToFirst()) null else Triple(c.getString(0), c.getLong(1), c.getLong(2))
            } ?: throw IllegalArgumentException("Product not found.")
            require(product.second >= qtyMilli) { "Insufficient stock. Available: " + formatQuantity(product.second) }
            val quantityValue = BigDecimal.valueOf(qtyMilli).movePointLeft(3)
            val totalPaisa = BigDecimal.valueOf(unitPrice).multiply(quantityValue).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
            require(totalPaisa > 0L) { "Sale amount is too small." }
            val values = ContentValues().apply {
                put("customer_id", customerId); put("transaction_date", date); put("transaction_type", "credit")
                put("description", description.trim().ifBlank { product.first }); put("amount_paisa", totalPaisa)
                put("created_at", now()); put("product_id", productId); put("quantity_milli", qtyMilli)
                put("unit_cost_paisa", product.third)
            }
            db.insertOrThrow("transactions", null, values)
            db.update("products", ContentValues().apply { put("stock_milli", product.second - qtyMilli) }, "id=?", arrayOf(productId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun addExpense(date: String, category: String, description: String, amount: Double): Long {
        require(LedgerMath.isValidDate(date) && category.isNotBlank())
        val values = ContentValues().apply {
            put("expense_date", date); put("category", category.trim().take(60))
            put("description", description.trim().take(240)); put("amount_paisa", toPaisa(amount)); put("created_at", now())
        }
        return writableDatabase.insertOrThrow("expenses", null, values)
    }

    fun updateExpense(id: Long, date: String, category: String, description: String, amount: Double) {
        require(LedgerMath.isValidDate(date) && category.isNotBlank())
        val values = ContentValues().apply {
            put("expense_date", date); put("category", category.trim().take(60))
            put("description", description.trim().take(240)); put("amount_paisa", toPaisa(amount))
        }
        require(writableDatabase.update("expenses", values, "id=?", arrayOf(id.toString())) == 1) { "Expense not found." }
    }

    fun deleteExpense(id: Long): Int = writableDatabase.delete("expenses", "id=?", arrayOf(id.toString()))

    fun expenses(month: String? = null): List<ExpenseRow> {
        val sql = if (month == null) "SELECT id,expense_date,category,description,amount_paisa FROM expenses ORDER BY expense_date DESC,id DESC"
            else "SELECT id,expense_date,category,description,amount_paisa FROM expenses WHERE substr(expense_date,1,7)=? ORDER BY expense_date DESC,id DESC"
        return readableDatabase.rawQuery(sql, if (month == null) null else arrayOf(month)).use { c ->
            buildList { while (c.moveToNext()) add(ExpenseRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4) / 100.0)) }
        }
    }

    fun profitAndLoss(month: String): ProfitLossSnapshot {
        require(Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(month))
        val salesCost = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN quantity_milli IS NOT NULL THEN amount_paisa ELSE 0 END),0),COALESCE(SUM(CASE WHEN quantity_milli IS NOT NULL THEN CAST(ROUND(quantity_milli * unit_cost_paisa / 1000.0) AS INTEGER) ELSE 0 END),0),COALESCE(SUM(CASE WHEN quantity_milli IS NOT NULL THEN 1 ELSE 0 END),0) FROM transactions WHERE substr(transaction_date,1,7)=?",
            arrayOf(month)).use { c -> c.moveToFirst(); Triple(c.getLong(0), c.getLong(1), c.getInt(2)) }
        val expenseTotals = readableDatabase.rawQuery("SELECT COALESCE(SUM(amount_paisa),0),COUNT(*) FROM expenses WHERE substr(expense_date,1,7)=?", arrayOf(month)).use { c -> c.moveToFirst(); Pair(c.getLong(0), c.getInt(1)) }
        val inventory = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(CAST(ROUND(stock_milli * purchase_price_paisa / 1000.0) AS INTEGER)),0),COALESCE(SUM(CASE WHEN stock_milli <= low_stock_milli THEN 1 ELSE 0 END),0) FROM products",
            null).use { c -> c.moveToFirst(); Pair(c.getLong(0), c.getInt(1)) }
        val sales = salesCost.first / 100.0
        val cost = salesCost.second / 100.0
        val expenseAmount = expenseTotals.first / 100.0
        return ProfitLossSnapshot(month, sales, cost, sales - cost, expenseAmount, sales - cost - expenseAmount,
            expenseTotals.second, inventory.first / 100.0, inventory.second, salesCost.third)
    }

    private fun toPaisaNonNegative(value: Double): Long {
        require(value >= 0.0 && value.isFinite() && value <= 9_000_000_000_000.0)
        return BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun toMilli(value: Double, allowZero: Boolean = true): Long {
        require(value >= 0.0 && value.isFinite() && value <= 9_000_000_000.0)
        val result = BigDecimal.valueOf(value).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(allowZero || result > 0L)
        return result
    }

    private fun formatQuantity(value: Long): String =
        BigDecimal.valueOf(value).movePointLeft(3).stripTrailingZeros().toPlainString()

    fun totals(): Triple<Double, Double, Int> =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount_paisa ELSE 0 END) / 100.0,0),COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount_paisa ELSE 0 END) / 100.0,0),COUNT(*) FROM transactions",
            null
        ).use {
            it.moveToFirst()
            Triple(it.getDouble(0), it.getDouble(1), it.getInt(2))
        }

    fun monthTotals(month: String): Pair<Double, Double> =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount_paisa ELSE 0 END) / 100.0,0),COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount_paisa ELSE 0 END) / 100.0,0) FROM transactions WHERE substr(transaction_date,1,7)=?",
            arrayOf(month)
        ).use {
            it.moveToFirst()
            Pair(it.getDouble(0), it.getDouble(1))
        }

    fun monthTransactionCount(month: String): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE substr(transaction_date,1,7)=?",
            arrayOf(month)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    /**
     * Search all local ledger domains without sending customer data to a server.
     * Results are capped so the query stays responsive on entry-level Android phones.
     */
    fun searchEverything(rawQuery: String): List<LedgerSearchResult> {
        val query = rawQuery.trim()
        if (query.isBlank()) return emptyList()
        val like = "%$query%"
        val numeric = query.replace(Regex("(?i)rs\\.?"), "").replace(",", "").trim()
        val results = ArrayList<LedgerSearchResult>()

        customers(query).take(20).forEach { customer ->
            results.add(
                LedgerSearchResult(
                    "Customer",
                    customer.name,
                    (if (customer.phone.isBlank()) "No phone number" else customer.phone) +
                        " · Balance Rs. " + String.format(Locale.US, "%,.2f", customer.balance),
                    customer.id
                )
            )
        }

        products().asSequence()
            .filter { it.name.contains(query, true) || it.sku.contains(query, true) }
            .take(20)
            .forEach { product ->
                results.add(
                    LedgerSearchResult(
                        "Product",
                        product.name,
                        "SKU: ${product.sku.ifBlank { "—" }} · Stock: ${product.stockQuantity} ${product.unit}"
                    )
                )
            }

        readableDatabase.rawQuery(
            """SELECT c.id,c.name,t.transaction_date,t.transaction_type,t.description,t.amount_paisa
               FROM transactions t JOIN customers c ON c.id=t.customer_id
               WHERE t.transaction_date LIKE ? OR t.description LIKE ? OR c.name LIKE ?
                  OR CAST(t.amount_paisa AS TEXT) LIKE ?
                  OR CAST(ROUND(t.amount_paisa / 100.0, 2) AS TEXT) LIKE ?
               ORDER BY t.transaction_date DESC,t.id DESC LIMIT 30""",
            arrayOf(like, like, like, "%$numeric%", "%$numeric%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val amount = cursor.getLong(5) / 100.0
                results.add(
                    LedgerSearchResult(
                        "Entry",
                        cursor.getString(1) + " · " + if (cursor.getString(3) == "credit") "Sale / credit" else "Payment",
                        cursor.getString(2) + " · Rs. " + String.format(Locale.US, "%,.2f", amount) +
                            if (cursor.getString(4).isBlank()) "" else " · " + cursor.getString(4),
                        cursor.getLong(0)
                    )
                )
            }
        }
        return results.distinctBy { Triple(it.kind, it.title, it.detail) }.take(60)
    }

    /** Amounts belonging to transactions dated on the selected local day. */
    fun dailySummary(date: String): Triple<Double, Double, Double> {
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date))
        return readableDatabase.rawQuery(
            """SELECT
                COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount_paisa ELSE 0 END),0),
                COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount_paisa ELSE 0 END),0)
               FROM transactions WHERE transaction_date=?""",
            arrayOf(date)
        ).use { cursor ->
            if (!cursor.moveToFirst()) Triple(0.0, 0.0, 0.0)
            else {
                val credit = cursor.getLong(0) / 100.0
                val payments = cursor.getLong(1) / 100.0
                Triple(credit, payments, (credit - payments).coerceAtLeast(0.0))
            }
        }
    }

    /**
     * Apply payments to the oldest customer credits first so recent sales do not hide
     * older unpaid balances from the weekly overdue reminder.
     */
    fun overdueCustomerSummary(cutoffDate: String): Pair<Int, Double> {
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(cutoffDate))
        var overdueCustomers = 0
        var overduePaisa = 0L
        customers().forEach { customer ->
            val outstanding = ArrayList<Pair<String, Long>>()
            transactions(customer.id)
                .sortedWith(compareBy<TransactionRow> { it.date }.thenBy { it.id })
                .forEach { entry ->
                    val amountPaisa = Math.round(entry.amount * 100.0)
                    if (entry.type == "credit") {
                        outstanding.add(entry.date to amountPaisa)
                    } else {
                        var remaining = amountPaisa
                        var index = 0
                        while (remaining > 0L && index < outstanding.size) {
                            val (date, amount) = outstanding[index]
                            val applied = minOf(amount, remaining)
                            outstanding[index] = date to (amount - applied)
                            remaining -= applied
                            index++
                        }
                    }
                }
            val overdueForCustomer = outstanding
                .filter { (date, amount) -> date <= cutoffDate && amount > 0L }
                .sumOf { it.second }
            if (overdueForCustomer > 0L) {
                overdueCustomers++
                overduePaisa += overdueForCustomer
            }
        }
        return overdueCustomers to (overduePaisa / 100.0)
    }

    fun exportBackup(): String {
        val root = org.json.JSONObject()
            .put("format", "ai-khata-backup")
            .put("version", 2)
            .put("shop_name", shopName())
            .put("exported_at", now())
        val people = org.json.JSONArray()
        customers().forEach { customer ->
            val person = org.json.JSONObject()
                .put("name", customer.name)
                .put("phone", customer.phone)
            val entries = org.json.JSONArray()
            transactions(customer.id).forEach { tx ->
                val item = org.json.JSONObject().put("date", tx.date).put("type", tx.type)
                    .put("description", tx.description).put("amount", tx.amount)
                tx.productId?.let { item.put("product_id", it) }
                tx.quantity?.let { item.put("quantity", it) }
                tx.unitCost?.let { item.put("unit_cost", it) }
                entries.put(item)
            }
            person.put("transactions", entries)
            people.put(person)
        }
        root.put("customers", people)
        val productArray = org.json.JSONArray()
        products().forEach { product ->
            productArray.put(org.json.JSONObject().put("id", product.id).put("name", product.name)
                .put("sku", product.sku).put("unit", product.unit).put("purchase_price", product.purchasePrice)
                .put("selling_price", product.sellingPrice).put("stock", product.stockQuantity)
                .put("low_stock", product.lowStockThreshold))
        }
        root.put("products", productArray)
        val expenseArray = org.json.JSONArray()
        expenses().forEach { expense ->
            expenseArray.put(org.json.JSONObject().put("date", expense.date).put("category", expense.category)
                .put("description", expense.description).put("amount", expense.amount))
        }
        root.put("expenses", expenseArray)
        return root.toString(2)
    }

    fun restoreBackup(json: String) {
        val root = org.json.JSONObject(json)
        val backupVersion = root.optInt("version")
        require(root.optString("format") == "ai-khata-backup" && backupVersion in 1..2) {
            "This is not a supported AI Khata backup."
        }
        val people = root.optJSONArray("customers")
            ?: throw IllegalArgumentException("Backup has no customer list.")
        validateBackupContents(root, people, backupVersion)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("transactions", null, null)
            db.delete("expenses", null, null)
            db.delete("products", null, null)
            db.delete("customers", null, null)
            val shop = root.optString("shop_name", "My shop").take(80)
            db.execSQL("UPDATE owner SET shop_name=? WHERE id=1", arrayOf(shop))
            val productIds = HashMap<Long, Long>()
            if (backupVersion >= 2) {
                val productArray = root.optJSONArray("products") ?: org.json.JSONArray()
                for (i in 0 until productArray.length()) {
                    val item = productArray.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val purchase = item.optDouble("purchase_price", Double.NaN)
                    val selling = item.optDouble("selling_price", Double.NaN)
                    val stock = item.optDouble("stock", Double.NaN)
                    val lowStock = item.optDouble("low_stock", 5.0)
                    if (name.isBlank() || !purchase.isFinite() || purchase < 0 || !selling.isFinite() ||
                        selling <= 0 || !stock.isFinite() || stock < 0 || !lowStock.isFinite() || lowStock < 0) continue
                    val values = ContentValues().apply {
                        put("name", name.take(100)); put("sku", item.optString("sku").take(50))
                        put("unit", item.optString("unit", "pcs").ifBlank { "pcs" }.take(20))
                        put("purchase_price_paisa", toPaisaNonNegative(purchase)); put("selling_price_paisa", toPaisa(selling))
                        put("stock_milli", toMilli(stock)); put("low_stock_milli", toMilli(lowStock)); put("created_at", now())
                    }
                    productIds[item.optLong("id", -1L)] = db.insertOrThrow("products", null, values)
                }
                val expenseArray = root.optJSONArray("expenses") ?: org.json.JSONArray()
                for (i in 0 until expenseArray.length()) {
                    val item = expenseArray.optJSONObject(i) ?: continue
                    val day = item.optString("date")
                    val category = item.optString("category").trim()
                    val amount = item.optDouble("amount", Double.NaN)
                    if (!LedgerMath.isValidDate(day) || category.isBlank() || !amount.isFinite() || amount <= 0) continue
                    val values = ContentValues().apply {
                        put("expense_date", day); put("category", category.take(60))
                        put("description", item.optString("description").take(240))
                        put("amount_paisa", toPaisa(amount)); put("created_at", now())
                    }
                    db.insertOrThrow("expenses", null, values)
                }
            }
            for (i in 0 until people.length()) {
                val person = people.optJSONObject(i) ?: continue
                val name = person.optString("name").trim()
                if (name.isBlank()) continue
                val customerValues = ContentValues().apply {
                    put("name", name)
                    put("phone", person.optString("phone").take(30))
                    put("created_at", now())
                }
                val customerId = db.insertOrThrow("customers", null, customerValues)
                val entries = person.optJSONArray("transactions") ?: org.json.JSONArray()
                for (j in 0 until entries.length()) {
                    val tx = entries.optJSONObject(j) ?: continue
                    val type = tx.optString("type")
                    val amount = tx.optDouble("amount", Double.NaN)
                    val day = tx.optString("date")
                    if (type !in listOf("credit", "payment") || !amount.isFinite() || amount <= 0 ||
                        !LedgerMath.isValidDate(day)) continue
                    val values = ContentValues().apply {
                        put("customer_id", customerId)
                        put("transaction_date", day)
                        put("transaction_type", type)
                        put("description", tx.optString("description").take(240))
                        put("amount_paisa", toPaisa(amount))
                        put("created_at", now())
                        val oldProductId = tx.optLong("product_id", -1L)
                        val newProductId = productIds[oldProductId]
                        val quantity = tx.optDouble("quantity", Double.NaN)
                        val unitCost = tx.optDouble("unit_cost", Double.NaN)
                        // Keep historical cost snapshots even when the product was later deleted.
                        if (quantity.isFinite() && quantity > 0 && unitCost.isFinite() && unitCost >= 0) {
                            if (newProductId != null) put("product_id", newProductId)
                            put("quantity_milli", toMilli(quantity, allowZero = false))
                            put("unit_cost_paisa", toPaisaNonNegative(unitCost))
                        }
                    }
                    db.insertOrThrow("transactions", null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun validateBackupContents(
        root: org.json.JSONObject,
        people: org.json.JSONArray,
        backupVersion: Int
    ) {
        val productIds = mutableSetOf<Long>()
        if (backupVersion >= 2) {
            val productsValue = root.opt("products")
            require(productsValue == null || productsValue == org.json.JSONObject.NULL ||
                productsValue is org.json.JSONArray) { "The backup product list is damaged." }
            val products = root.optJSONArray("products") ?: org.json.JSONArray()
            for (index in 0 until products.length()) {
                val item = products.optJSONObject(index)
                    ?: throw IllegalArgumentException("A product record in this backup is damaged.")
                val id = item.optLong("id", -1L)
                val name = item.optString("name").trim()
                val purchase = item.optDouble("purchase_price", Double.NaN)
                val selling = item.optDouble("selling_price", Double.NaN)
                val stock = item.optDouble("stock", Double.NaN)
                val lowStock = item.optDouble("low_stock", 5.0)
                require(id > 0 && productIds.add(id) && name.isNotBlank() &&
                    purchase.isFinite() && purchase >= 0.0 && purchase <= 9_000_000_000_000.0 &&
                    selling.isFinite() && selling > 0.0 && selling <= 9_000_000_000_000.0 &&
                    stock.isFinite() && stock >= 0.0 && stock <= 9_000_000_000.0 &&
                    lowStock.isFinite() && lowStock >= 0.0 && lowStock <= 9_000_000_000.0
                ) { "A product record in this backup is invalid." }
            }
            val expensesValue = root.opt("expenses")
            require(expensesValue == null || expensesValue == org.json.JSONObject.NULL ||
                expensesValue is org.json.JSONArray) { "The backup expense list is damaged." }
            val expenses = root.optJSONArray("expenses") ?: org.json.JSONArray()
            for (index in 0 until expenses.length()) {
                val item = expenses.optJSONObject(index)
                    ?: throw IllegalArgumentException("An expense record in this backup is damaged.")
                val day = item.optString("date")
                val category = item.optString("category").trim()
                val amount = item.optDouble("amount", Double.NaN)
                require(LedgerMath.isValidDate(day) && category.isNotBlank() &&
                    amount.isFinite() && amount > 0.0 && amount <= 9_000_000_000_000.0
                ) { "An expense record in this backup is invalid." }
            }
        }

        for (index in 0 until people.length()) {
            val person = people.optJSONObject(index)
                ?: throw IllegalArgumentException("A customer record in this backup is damaged.")
            require(person.optString("name").trim().isNotBlank()) {
                "A customer record has no name."
            }
            val transactionsValue = person.opt("transactions")
            require(transactionsValue == null || transactionsValue == org.json.JSONObject.NULL ||
                transactionsValue is org.json.JSONArray) { "A customer transaction list is damaged." }
            val entries = person.optJSONArray("transactions") ?: org.json.JSONArray()
            for (entryIndex in 0 until entries.length()) {
                val tx = entries.optJSONObject(entryIndex)
                    ?: throw IllegalArgumentException("A transaction in this backup is damaged.")
                val type = tx.optString("type")
                val amount = tx.optDouble("amount", Double.NaN)
                val day = tx.optString("date")
                require(type in listOf("credit", "payment") && amount.isFinite() &&
                    amount > 0.0 && amount <= 9_000_000_000_000.0 && LedgerMath.isValidDate(day)
                ) { "A transaction in this backup is invalid." }

                val hasProductId = tx.has("product_id") && !tx.isNull("product_id")
                if (hasProductId) {
                    require(backupVersion >= 2 && productIds.contains(tx.optLong("product_id", -1L))) {
                        "A transaction refers to a product that is missing from this backup."
                    }
                }
                val hasQuantity = tx.has("quantity") && !tx.isNull("quantity")
                val hasUnitCost = tx.has("unit_cost") && !tx.isNull("unit_cost")
                require(hasQuantity == hasUnitCost) { "A sale cost snapshot in this backup is incomplete." }
                if (hasQuantity) {
                    val quantity = tx.optDouble("quantity", Double.NaN)
                    val unitCost = tx.optDouble("unit_cost", Double.NaN)
                    require(quantity.isFinite() && quantity > 0.0 && quantity <= 9_000_000_000.0 &&
                        unitCost.isFinite() && unitCost >= 0.0 && unitCost <= 9_000_000_000_000.0
                    ) { "A sale cost snapshot in this backup is invalid." }
                }
            }
        }
    }

    private fun toPaisa(amount: Double): Long = LedgerMath.toPaisa(amount)

    private fun derive(pin: String, salt: ByteArray) =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120000, 256)).encoded

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun now() = java.text.SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US
    ).format(java.util.Date())
}
