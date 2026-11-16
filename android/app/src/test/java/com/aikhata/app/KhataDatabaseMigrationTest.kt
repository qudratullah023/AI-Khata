package com.aikhata.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KhataDatabaseMigrationTest {
    @Test
    fun upgradesLegacyRupeeAmountsWithoutLosingRecordsOrForeignKeys() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_khata_local.db")

        val legacy = context.openOrCreateDatabase(
            "ai_khata_local.db",
            Context.MODE_PRIVATE,
            null
        )
        legacy.execSQL(
            "CREATE TABLE owner (id INTEGER PRIMARY KEY CHECK(id=1), shop_name TEXT NOT NULL, pin_salt TEXT NOT NULL, pin_hash TEXT NOT NULL, created_at TEXT NOT NULL)"
        )
        legacy.execSQL(
            "CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)"
        )
        legacy.execSQL(
            "CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE, transaction_date TEXT NOT NULL, transaction_type TEXT NOT NULL CHECK(transaction_type IN ('credit','payment')), description TEXT NOT NULL DEFAULT '', amount REAL NOT NULL CHECK(amount > 0), created_at TEXT NOT NULL)"
        )
        legacy.execSQL(
            "INSERT INTO customers(id,name,phone,created_at) VALUES (7,'Amina','03001234567','2026-11-16T10:00:00')"
        )
        legacy.execSQL(
            "INSERT INTO transactions(id,customer_id,transaction_date,transaction_type,description,amount,created_at) VALUES (19,7,'2026-11-16','credit','Rice',1234.50,'2026-11-16T10:00:00')"
        )
        legacy.execSQL(
            "INSERT INTO transactions(id,customer_id,transaction_date,transaction_type,description,amount,created_at) VALUES (20,7,'2026-11-16','payment','Small amount',0.001,'2026-11-16T10:01:00')"
        )
        legacy.execSQL(
            "INSERT INTO transactions(id,customer_id,transaction_date,transaction_type,description,amount,created_at) VALUES (21,7,'2026-11-16','credit','Half-up amount',1.005,'2026-11-16T10:02:00')"
        )
        legacy.version = 1
        legacy.close()

        val helper = KhataDatabase(context)
        val migrated = helper.readableDatabase
        val rows = migrated.rawQuery(
            "SELECT id, customer_id, amount_paisa, description FROM transactions ORDER BY id",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        listOf(
                            cursor.getLong(0),
                            cursor.getLong(1),
                            cursor.getLong(2),
                            cursor.getString(3)
                        )
                    )
                }
            }
        }

        assertEquals(
            listOf(
                listOf(19L, 7L, 123450L, "Rice"),
                listOf(20L, 7L, 1L, "Small amount"),
                listOf(21L, 7L, 101L, "Half-up amount")
            ),
            rows
        )
        assertEquals(
            "Amina",
            migrated.rawQuery("SELECT name FROM customers WHERE id=7", null).use {
                assertTrue(it.moveToFirst())
                it.getString(0)
            }
        )
        assertEquals(
            0,
            migrated.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
        )
        assertEquals(2, migrated.version)

        helper.close()
        context.deleteDatabase("ai_khata_local.db")
    }
}
