package com.aikhata.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ShopProfile(val id: String, val name: String, val databaseName: String)

object ShopRegistry {
    private const val PREFS = "ai_khata_shop_registry"
    private const val SHOPS = "shops_json"
    private const val ACTIVE = "active_shop_id"

    @Synchronized
    fun shops(context: Context): List<ShopProfile> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(SHOPS, null)
        if (raw.isNullOrBlank()) {
            val default = ShopProfile("default", "My shop", "ai_khata_local.db")
            save(context, listOf(default))
            prefs.edit().putString(ACTIVE, "default").apply()
            return listOf(default)
        }
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val database = item.optString("database").takeIf { it.isNotBlank() } ?: continue
                    add(ShopProfile(id, item.optString("name", "My shop"), database))
                }
            }.ifEmpty { listOf(ShopProfile("default", "My shop", "ai_khata_local.db")).also { save(context, it) } }
        } catch (_: Exception) {
            listOf(ShopProfile("default", "My shop", "ai_khata_local.db")).also { save(context, it) }
        }
    }

    fun activeShopId(context: Context): String {
        val list = shops(context)
        val selected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE, "default")
        return list.firstOrNull { it.id == selected }?.id ?: list.first().id
    }

    fun activeDatabaseName(context: Context): String {
        val id = activeShopId(context)
        return shops(context).firstOrNull { it.id == id }?.databaseName ?: "ai_khata_local.db"
    }

    fun activeShop(context: Context): ShopProfile =
        shops(context).first { it.id == activeShopId(context) }

    @Synchronized
    fun updateActiveName(context: Context, name: String) {
        val id = activeShopId(context)
        val updated = shops(context).map { if (it.id == id) it.copy(name = name.take(80)) else it }
        save(context, updated)
    }

    @Synchronized
    fun createShop(context: Context, name: String, pin: String): ShopProfile {
        require(name.isNotBlank()) { "Enter a shop name." }
        require(pin.length >= 6 && pin.all(Char::isDigit)) { "Use a PIN with at least 6 digits." }
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        val databaseName = "ai_khata_shop_$id.db"
        val newDatabase = KhataDatabase(context, databaseName)
        try {
            newDatabase.createOwner(name.trim().take(80), pin)
        } finally {
            newDatabase.close()
        }
        val profile = ShopProfile(id, name.trim().take(80), databaseName)
        save(context, shops(context) + profile)
        return profile
    }

    fun switchTo(context: Context, shopId: String) {
        require(shops(context).any { it.id == shopId }) { "Shop not found." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE, shopId).apply()
    }

    private fun save(context: Context, shops: List<ShopProfile>) {
        val json = JSONArray()
        shops.forEach { shop ->
            json.put(JSONObject().put("id", shop.id).put("name", shop.name).put("database", shop.databaseName))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SHOPS, json.toString()).apply()
    }
}
