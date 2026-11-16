package com.aikhata.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class CustomerRow(val id: Long, val name: String, val phone: String, val balance: Double)
data class TransactionRow(val id: Long, val date: String, val type: String, val description: String, val amount: Double)

class KhataDatabase(context: Context) : SQLiteOpenHelper(context, "ai_khata_local.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE owner (id INTEGER PRIMARY KEY CHECK(id=1), shop_name TEXT NOT NULL, pin_salt TEXT NOT NULL, pin_hash TEXT NOT NULL, created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE, transaction_date TEXT NOT NULL, transaction_type TEXT NOT NULL CHECK(transaction_type IN ('credit','payment')), description TEXT NOT NULL DEFAULT '', amount REAL NOT NULL CHECK(amount > 0), created_at TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_local_tx_customer_date ON transactions(customer_id, transaction_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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
        // The database foreign-key rule also removes this customer's transactions.
        return writableDatabase.delete("customers", "id=?", arrayOf(id.toString()))
    }

    fun customers(query: String = ""): List<CustomerRow> =
        readableDatabase.rawQuery(
            """SELECT c.id,c.name,c.phone,
            COALESCE(SUM(CASE WHEN t.transaction_type='credit' THEN t.amount ELSE -t.amount END),0)
            FROM customers c LEFT JOIN transactions t ON t.customer_id=c.id
            WHERE c.name LIKE ? OR c.phone LIKE ?
            GROUP BY c.id ORDER BY c.name COLLATE NOCASE""",
            arrayOf("%$query%", "%$query%")
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(CustomerRow(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getDouble(3)))
                }
            }
        }

    fun customer(id: Long) = customers().firstOrNull { it.id == id }

    fun addTransaction(id: Long, type: String, amount: Double, description: String, date: String) {
        require(type == "credit" || type == "payment")
        require(amount > 0 && amount.isFinite())
        val values = ContentValues().apply {
            put("customer_id", id)
            put("transaction_date", date)
            put("transaction_type", type)
            put("description", description.trim())
            put("amount", amount)
            put("created_at", now())
        }
        writableDatabase.insertOrThrow("transactions", null, values)
    }

    fun updateTransaction(id: Long, amount: Double, description: String, date: String) {
        require(amount > 0 && amount.isFinite())
        val values = ContentValues().apply {
            put("amount", amount)
            put("description", description.trim())
            put("transaction_date", date)
        }
        writableDatabase.update("transactions", values, "id=?", arrayOf(id.toString()))
    }

    fun deleteTransaction(id: Long): Int {
        return writableDatabase.delete("transactions", "id=?", arrayOf(id.toString()))
    }

    fun transactions(id: Long): List<TransactionRow> =
        readableDatabase.rawQuery(
            "SELECT id,transaction_date,transaction_type,description,amount FROM transactions WHERE customer_id=? ORDER BY transaction_date DESC,id DESC",
            arrayOf(id.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(TransactionRow(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getDouble(4)
                    ))
                }
            }
        }

    fun totals(): Triple<Double, Double, Int> =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount ELSE 0 END),0),COUNT(*) FROM transactions",
            null
        ).use {
            it.moveToFirst()
            Triple(it.getDouble(0), it.getDouble(1), it.getInt(2))
        }

    fun monthTotals(month: String): Pair<Double, Double> =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount ELSE 0 END),0) FROM transactions WHERE substr(transaction_date,1,7)=?",
            arrayOf(month)
        ).use {
            it.moveToFirst()
            Pair(it.getDouble(0), it.getDouble(1))
        }

    fun exportBackup(): String {
        val root = org.json.JSONObject()
            .put("format", "ai-khata-backup")
            .put("version", 1)
            .put("shop_name", shopName())
            .put("exported_at", now())
        val people = org.json.JSONArray()
        customers().forEach { customer ->
            val person = org.json.JSONObject()
                .put("name", customer.name)
                .put("phone", customer.phone)
            val entries = org.json.JSONArray()
            transactions(customer.id).forEach { tx ->
                entries.put(org.json.JSONObject()
                    .put("date", tx.date)
                    .put("type", tx.type)
                    .put("description", tx.description)
                    .put("amount", tx.amount))
            }
            person.put("transactions", entries)
            people.put(person)
        }
        root.put("customers", people)
        return root.toString(2)
    }

    fun restoreBackup(json: String) {
        val root = org.json.JSONObject(json)
        require(root.optString("format") == "ai-khata-backup" && root.optInt("version") == 1) {
            "This is not a supported AI Khata backup."
        }
        val people = root.optJSONArray("customers")
            ?: throw IllegalArgumentException("Backup has no customer list.")
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("transactions", null, null)
            db.delete("customers", null, null)
            val shop = root.optString("shop_name", "My shop").take(80)
            db.execSQL("UPDATE owner SET shop_name=? WHERE id=1", arrayOf(shop))
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
                        !day.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) continue
                    val values = ContentValues().apply {
                        put("customer_id", customerId)
                        put("transaction_date", day)
                        put("transaction_type", type)
                        put("description", tx.optString("description").take(240))
                        put("amount", amount)
                        put("created_at", now())
                    }
                    db.insertOrThrow("transactions", null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun derive(pin: String, salt: ByteArray) =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120000, 256)).encoded

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun now() = java.text.SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US
    ).format(java.util.Date())
}
