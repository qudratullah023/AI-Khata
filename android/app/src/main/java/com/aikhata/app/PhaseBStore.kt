package com.aikhata.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class CustomerMeta(
    val customerId: Long,
    val tags: String = "",
    val notes: String = "",
    val shareOptIn: Boolean = false
)

data class SupplierRow(
    val id: Long,
    val name: String,
    val phone: String,
    val balance: Double
)

data class SupplierEntryRow(
    val id: Long,
    val date: String,
    val type: String,
    val description: String,
    val amount: Double
)

data class WalletReferences(val jazzCash: String = "", val easypaisa: String = "")

class PhaseBStore(context: Context, shopId: String) : SQLiteOpenHelper(
    context,
    "ai_khata_phase_b_${shopId.replace(Regex("[^A-Za-z0-9_]"), "_")}.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customer_meta (customer_id INTEGER PRIMARY KEY, tags TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', share_opt_in INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE supplier_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE, entry_date TEXT NOT NULL, entry_type TEXT NOT NULL CHECK(entry_type IN ('payable','payment')), description TEXT NOT NULL DEFAULT '', amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_supplier_entries_supplier_date ON supplier_entries(supplier_id, entry_date)")
        db.execSQL("CREATE TABLE settings (setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun customerMeta(customerId: Long): CustomerMeta =
        readableDatabase.rawQuery(
            "SELECT tags,notes,share_opt_in FROM customer_meta WHERE customer_id=?",
            arrayOf(customerId.toString())
        ).use { c ->
            if (c.moveToFirst()) CustomerMeta(customerId, c.getString(0), c.getString(1), c.getInt(2) == 1)
            else CustomerMeta(customerId)
        }

    fun saveCustomerMeta(meta: CustomerMeta) {
        val values = ContentValues().apply {
            put("customer_id", meta.customerId)
            put("tags", meta.tags.trim().take(240))
            put("notes", meta.notes.trim().take(2000))
            put("share_opt_in", if (meta.shareOptIn) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("customer_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun walletReferences() = WalletReferences(
        getSetting("wallet_jazzcash"),
        getSetting("wallet_easypaisa")
    )

    fun saveWalletReferences(refs: WalletReferences) {
        putSetting("wallet_jazzcash", refs.jazzCash.trim().take(40))
        putSetting("wallet_easypaisa", refs.easypaisa.trim().take(40))
    }

    fun getSetting(key: String, default: String = ""): String =
        readableDatabase.rawQuery("SELECT setting_value FROM settings WHERE setting_key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else default
        }

    fun putSetting(key: String, value: String) {
        val values = ContentValues().apply { put("setting_key", key); put("setting_value", value) }
        writableDatabase.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setStaffPin(pin: String) {
        require(pin.length >= 6 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        putSetting("staff_pin_salt", salt.toHex())
        putSetting("staff_pin_hash", derive(pin, salt).toHex())
        putSetting("staff_pin_enabled", "1")
    }

    fun hasStaffPin(): Boolean = getSetting("staff_pin_enabled") == "1"

    fun verifyStaffPin(pin: String): Boolean {
        if (!hasStaffPin()) return false
        val salt = runCatching { getSetting("staff_pin_salt").fromHex() }.getOrNull() ?: return false
        val expected = runCatching { getSetting("staff_pin_hash").fromHex() }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(pin, salt))
    }

    fun clearStaffPin() {
        listOf("staff_pin_salt", "staff_pin_hash", "staff_pin_enabled").forEach { putSetting(it, "") }
    }

    fun addSupplier(name: String, phone: String): Long {
        require(name.isNotBlank()) { "Supplier name is required." }
        val values = ContentValues().apply {
            put("name", name.trim().take(100))
            put("phone", phone.trim().take(40))
            put("created_at", now())
        }
        return writableDatabase.insertOrThrow("suppliers", null, values)
    }

    fun suppliers(): List<SupplierRow> =
        readableDatabase.rawQuery(
            """SELECT s.id,s.name,s.phone,
                COALESCE(SUM(CASE WHEN e.entry_type='payable' THEN e.amount_paisa ELSE -e.amount_paisa END),0)/100.0
                FROM suppliers s LEFT JOIN supplier_entries e ON e.supplier_id=s.id
                GROUP BY s.id,s.name,s.phone ORDER BY s.name COLLATE NOCASE""",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(SupplierRow(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3)))
            }
        }

    fun addSupplierEntry(supplierId: Long, type: String, amount: Double, description: String, date: String = today()) {
        require(type == "payable" || type == "payment") { "Invalid supplier entry type." }
        require(amount.isFinite() && amount > 0.0) { "Enter an amount greater than zero." }
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) { "Use a valid date." }
        val paisa = Math.round(amount * 100.0)
        require(paisa > 0)
        val values = ContentValues().apply {
            put("supplier_id", supplierId)
            put("entry_date", date)
            put("entry_type", type)
            put("description", description.trim().take(250))
            put("amount_paisa", paisa)
            put("created_at", now())
        }
        writableDatabase.insertOrThrow("supplier_entries", null, values)
    }

    fun supplierEntries(supplierId: Long): List<SupplierEntryRow> =
        readableDatabase.rawQuery(
            "SELECT id,entry_date,entry_type,description,amount_paisa FROM supplier_entries WHERE supplier_id=? ORDER BY entry_date DESC,id DESC",
            arrayOf(supplierId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    SupplierEntryRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4) / 100.0)
                )
            }
        }

    fun overdueSupplierSummary(cutoffDate: String): Pair<Int, Double> {
        val rows = suppliers()
        val overdue = rows.filter { supplier ->
            supplier.balance > 0.009 && supplierEntries(supplier.id).any { it.type == "payable" && it.date <= cutoffDate }
        }
        return overdue.size to overdue.sumOf { it.balance }
    }

    private fun now() = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())
    private fun today() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
