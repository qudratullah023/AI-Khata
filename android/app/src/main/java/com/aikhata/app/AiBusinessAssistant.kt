package com.aikhata.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AssistantDraft(val reply: String, val entries: JSONArray)

/** Creates reviewable drafts only. It never writes ledger data itself. */
class AiBusinessAssistant {
    fun interpret(instruction: String, customerNames: List<String>, apiKeys: List<String>): AssistantDraft {
        require(instruction.isNotBlank()) { "Write or dictate what happened first." }
        require(apiKeys.isNotEmpty()) { "Add a Gemini API key in Settings to use the AI assistant." }
        val today = java.time.LocalDate.now().toString()
        val prompt = """
You are the careful bookkeeping assistant inside AI Khata.
Understand English, Urdu written in Latin letters (Roman Urdu), and mixed language.
Turn only clearly stated transactions into proposed ledger entries.
Never invent a customer, date, amount, or payment direction.
If any required detail is missing or ambiguous, return no entries and ask a short question.
Use type credit when the shop sold goods / customer owes the shop.
Use type payment when the customer paid money to the shop.
Use ISO date YYYY-MM-DD. If no date is said, use $today.
Return ONLY JSON: {"reply":"short helpful response","entries":[{"customer_name":"...","date":"YYYY-MM-DD","type":"credit or payment","amount":123.0,"description":"..."}]}.
Nothing is saved until the shopkeeper reviews and confirms.
Known customer names (match carefully): ${customerNames.take(250).joinToString(" | ")}
Shopkeeper instruction: $instruction
""".trimIndent()
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.1))
        val models = listOf("gemini-2.5-flash", "gemini-2.0-flash")
        val failures = mutableListOf<String>()
        for ((keyIndex, key) in apiKeys.withIndex()) {
            for (model in models) {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 45000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("x-goog-api-key", key)
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        failures.add("$model / key ${keyIndex + 1}: HTTP $code")
                        continue
                    }
                    val answer = JSONObject(responseText).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                    val start = answer.indexOf('{')
                    val end = answer.lastIndexOf('}')
                    require(start >= 0 && end > start) { "AI returned an unreadable response." }
                    val result = JSONObject(answer.substring(start, end + 1))
                    val entries = result.optJSONArray("entries") ?: JSONArray()
                    validateEntries(entries)
                    return AssistantDraft(
                        result.optString("reply").ifBlank {
                            if (entries.length() == 0) "I need a little more information before preparing an entry."
                            else "Please review this proposed entry before saving."
                        }, entries
                    )
                } catch (error: Exception) {
                    failures.add(error.message ?: "AI request failed")
                } finally {
                    connection?.disconnect()
                }
            }
        }
        throw IllegalStateException("The AI assistant could not prepare a draft. " + failures.takeLast(2).joinToString("; "))
    }

    private fun validateEntries(entries: JSONArray) {
        require(entries.length() <= 20) { "Please record no more than 20 entries at a time." }
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: throw IllegalArgumentException("Invalid entry returned by AI.")
            val name = item.optString("customer_name").trim()
            val type = item.optString("type")
            val date = item.optString("date")
            val amount = item.optDouble("amount", Double.NaN)
            require(name.isNotBlank() && name.length <= 100) { "The customer name needs clarification." }
            require(type == "credit" || type == "payment") { "The transaction type needs clarification." }
            require(amount.isFinite() && amount > 0.0 && amount <= 100000000.0) { "The amount needs clarification." }
            require(LedgerMath.isValidDate(date)) { "The transaction date needs clarification." }
            item.put("customer_name", name)
            item.put("description", item.optString("description").take(240))
        }
    }
}
