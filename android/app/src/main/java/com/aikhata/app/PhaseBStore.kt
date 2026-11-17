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
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

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

    /**
     * Apply supplier payments to the oldest payable first. Only unpaid portions of
     * payables dated on or before the cutoff are counted as overdue.
     */
    fun overdueSupplierSummary(cutoffDate: String): Pair<Int, Double> {
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(cutoffDate))
        var overdueSuppliers = 0
        var overduePaisa = 0L
        suppliers().forEach { supplier ->
            val outstanding = ArrayList<Pair<String, Long>>()
            var supplierAdvancePaisa = 0L
            supplierEntries(supplier.id).asReversed().forEach { entry ->
                val entryPaisa = Math.round(entry.amount * 100.0)
                if (entry.type == "payable") {
                    val advanceUsed = minOf(supplierAdvancePaisa, entryPaisa)
                    supplierAdvancePaisa -= advanceUsed
                    val remainingPayable = entryPaisa - advanceUsed
                    if (remainingPayable > 0L) outstanding.add(entry.date to remainingPayable)
                } else {
                    var remaining = entryPaisa
                    var index = 0
                    while (remaining > 0L && index < outstanding.size) {
                        val (date, amount) = outstanding[index]
                        val applied = minOf(amount, remaining)
                        outstanding[index] = date to (amount - applied)
                        remaining -= applied
                        index++
                    }
                    supplierAdvancePaisa += remaining
                }
            }
            val overdueForSupplier = outstanding
                .filter { (date, amount) -> date <= cutoffDate && amount > 0L }
                .sumOf { it.second }
            if (overdueForSupplier > 0L) {
                overdueSuppliers++
                overduePaisa += overdueForSupplier
            }
        }
        return overdueSuppliers to (overduePaisa / 100.0)
    }


    /**
     * Include Phase B records in the encrypted shop backup. Customer IDs are preserved
     * by the main ledger backup so customer tags and sharing permissions reconnect safely.
     */
    fun exportBackupSnapshot(): org.json.JSONObject {
        val root = org.json.JSONObject()
        val metadata = org.json.JSONArray()
        readableDatabase.rawQuery(
            "SELECT customer_id,tags,notes,share_opt_in FROM customer_meta ORDER BY customer_id",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                metadata.put(org.json.JSONObject()
                    .put("customer_id", cursor.getLong(0))
                    .put("tags", cursor.getString(1))
                    .put("notes", cursor.getString(2))
                    .put("share_opt_in", cursor.getInt(3) == 1))
            }
        }
        val supplierArray = org.json.JSONArray()
        val entryArray = org.json.JSONArray()
        suppliers().forEach { supplier ->
            supplierArray.put(org.json.JSONObject()
                .put("id", supplier.id)
                .put("name", supplier.name)
                .put("phone", supplier.phone))
            supplierEntries(supplier.id).forEach { entry ->
                entryArray.put(org.json.JSONObject()
                    .put("id", entry.id)
                    .put("supplier_id", supplier.id)
                    .put("date", entry.date)
                    .put("type", entry.type)
                    .put("description", entry.description)
                    .put("amount_paisa", Math.round(entry.amount * 100.0)))
            }
        }
        val settings = org.json.JSONObject()
        readableDatabase.rawQuery("SELECT setting_key,setting_value FROM settings ORDER BY setting_key", null).use { cursor ->
            while (cursor.moveToNext()) settings.put(cursor.getString(0), cursor.getString(1))
        }
        root.put("customer_meta", metadata)
        root.put("suppliers", supplierArray)
        root.put("supplier_entries", entryArray)
        root.put("settings", settings)
        return root
    }

    fun restoreBackupSnapshot(snapshot: org.json.JSONObject?) {
        validateBackupSnapshot(snapshot)
        val source = snapshot ?: org.json.JSONObject()
        val metadata = source.optJSONArray("customer_meta") ?: org.json.JSONArray()
        val supplierArray = source.optJSONArray("suppliers") ?: org.json.JSONArray()
        val entryArray = source.optJSONArray("supplier_entries") ?: org.json.JSONArray()
        val settings = source.optJSONObject("settings") ?: org.json.JSONObject()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("supplier_entries", null, null)
            db.delete("suppliers", null, null)
            db.delete("customer_meta", null, null)
            db.delete("settings", null, null)
            for (index in 0 until supplierArray.length()) {
                val item = supplierArray.getJSONObject(index)
                val values = ContentValues().apply {
                    put("id", item.getLong("id"))
                    put("name", item.getString("name").trim().take(100))
                    put("phone", item.optString("phone").take(40))
                    put("created_at", now())
                }
                db.insertOrThrow("suppliers", null, values)
            }
            for (index in 0 until entryArray.length()) {
                val item = entryArray.getJSONObject(index)
                val values = ContentValues().apply {
                    put("id", item.getLong("id"))
                    put("supplier_id", item.getLong("supplier_id"))
                    put("entry_date", item.getString("date"))
                    put("entry_type", item.getString("type"))
                    put("description", item.optString("description").take(250))
                    put("amount_paisa", item.getLong("amount_paisa"))
                    put("created_at", now())
                }
                db.insertOrThrow("supplier_entries", null, values)
            }
            for (index in 0 until metadata.length()) {
                val item = metadata.getJSONObject(index)
                val values = ContentValues().apply {
                    put("customer_id", item.getLong("customer_id"))
                    put("tags", item.optString("tags").take(240))
                    put("notes", item.optString("notes").take(2000))
                    put("share_opt_in", if (item.optBoolean("share_opt_in")) 1 else 0)
                }
                db.insertOrThrow("customer_meta", null, values)
            }
            val keys = settings.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val values = ContentValues().apply {
                    put("setting_key", key.take(100))
                    put("setting_value", settings.optString(key).take(4096))
                }
                db.insertOrThrow("settings", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        fun validateBackupSnapshot(snapshot: org.json.JSONObject?) {
            if (snapshot == null) return
            fun array(name: String): org.json.JSONArray {
                val value = snapshot.opt(name)
                require(value == null || value == org.json.JSONObject.NULL || value is org.json.JSONArray) {
                    "The Phase B $name backup section is damaged."
                }
                return snapshot.optJSONArray(name) ?: org.json.JSONArray()
            }
            val metadata = array("customer_meta")
            val suppliers = array("suppliers")
            val entries = array("supplier_entries")
            val settings = snapshot.optJSONObject("settings") ?: org.json.JSONObject()
            require(settings.length() <= 100) { "The Phase B settings backup is too large." }
            val customerIds = mutableSetOf<Long>()
            for (index in 0 until metadata.length()) {
                val item = metadata.optJSONObject(index)
                    ?: throw IllegalArgumentException("A customer profile backup record is damaged.")
                val id = item.optLong("customer_id", -1L)
                require(id > 0 && customerIds.add(id) &&
                    item.optString("tags").length <= 240 && item.optString("notes").length <= 2000
                ) { "A customer profile backup record is invalid." }
            }
            val supplierIds = mutableSetOf<Long>()
            for (index in 0 until suppliers.length()) {
                val item = suppliers.optJSONObject(index)
                    ?: throw IllegalArgumentException("A supplier backup record is damaged.")
                val id = item.optLong("id", -1L)
                require(id > 0 && supplierIds.add(id) && item.optString("name").isNotBlank() &&
                    item.optString("name").length <= 100 && item.optString("phone").length <= 40
                ) { "A supplier backup record is invalid." }
            }
            val entryIds = mutableSetOf<Long>()
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index)
                    ?: throw IllegalArgumentException("A supplier entry backup record is damaged.")
                val id = item.optLong("id", -1L)
                val supplierId = item.optLong("supplier_id", -1L)
                val amount = item.optLong("amount_paisa", -1L)
                val date = item.optString("date")
                require(id > 0 && entryIds.add(id) && supplierIds.contains(supplierId) &&
                    item.optString("type") in listOf("payable", "payment") &&
                    Regex("^\\\\d{4}-\\\\d{2}-\\\\d{2}$").matches(date) &&
                    amount > 0 && amount <= 900000000000000L &&
                    item.optString("description").length <= 250
                ) { "A supplier entry backup record is invalid." }
            }
            val settingKeys = settings.keys()
            while (settingKeys.hasNext()) {
                val key = settingKeys.next()
                require(key.isNotBlank() && key.length <= 100 && settings.optString(key).length <= 4096) {
                    "A Phase B setting in this backup is invalid."
                }
            }
        }
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
