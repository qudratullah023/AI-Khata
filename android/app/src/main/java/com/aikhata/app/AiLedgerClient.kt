package com.aikhata.app

import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AiLedgerClient(private val backendUrl: String) {
    fun extract(imageBytes: ByteArray, installId: String, personalKeys: List<String>): JSONArray {
        var serverError: Exception? = null
        try {
            return extractFromBackend(imageBytes, installId)
        } catch (error: Exception) {
            serverError = error
        }

        if (personalKeys.isEmpty()) {
            throw IllegalStateException(
                serverError?.message ?: "The built-in AI service is not ready. Please try again later."
            )
        }

        val models = listOf(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-2.5-flash"
        )
        val prompt = "Read this handwritten shop ledger. Understand Urdu script, Roman Urdu, English and mixed numerals. Ignore crossed-out lines and do not record running totals or balances as new transactions. Return JSON with an entries array. Each entry must include customer_name, date YYYY-MM-DD, description, type credit or payment, amount as a number, and confidence from 0 to 1. Include page_total only when clearly written. Never guess unreadable names or amounts."
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put(
                "inline_data",
                JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
            ))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.1))

        val failures = ArrayList<String>()
        for (keyIndex in personalKeys.indices) {
            for (model in models) {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(
                        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                    ).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 45000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("x-goog-api-key", personalKeys[keyIndex])
                    connection.doOutput = true
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                    val code = connection.responseCode
                    val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        failures.add("$model / personal key ${keyIndex + 1}: HTTP $code")
                        continue
                    }
                    val response = JSONObject(responseText)
                    val answer = response.optJSONArray("candidates")
                        ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        ?.optJSONObject(0)?.optString("text").orEmpty()
                    val start = answer.indexOf('{')
                    val end = answer.lastIndexOf('}')
                    if (start >= 0 && end > start) {
                        val entries = JSONObject(answer.substring(start, end + 1)).optJSONArray("entries")
                        if (entries != null) return entries
                    }
                    failures.add("$model / personal key ${keyIndex + 1}: empty response")
                } catch (error: Exception) {
                    failures.add("$model / personal key ${keyIndex + 1}: ${error.message ?: "connection error"}")
                } finally {
                    connection?.disconnect()
                }
            }
        }
        throw IllegalStateException(
            "The built-in AI service and your personal fallback keys could not read the photo. " +
                failures.takeLast(3).joinToString("; ")
        )
    }

    private fun extractFromBackend(imageBytes: ByteArray, installId: String): JSONArray {
        val boundary = "AIKhata" + UUID.randomUUID().toString().replace("-", "")
        val connection = URL(backendUrl.trimEnd('/') + "/api/mobile/extract")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("X-Install-ID", installId)
            connection.setRequestProperty("Accept", "application/json")
            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"ledger_image\"; filename=\"ledger.jpg\"\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                output.write(imageBytes)
                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }
            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }
            if (code !in 200..299) {
                throw IllegalStateException(response.optString("error", "AI service returned HTTP $code."))
            }
            return response.optJSONArray("entries")
                ?: throw IllegalStateException("The AI service returned no entries. Try a clearer photo.")
        } finally {
            connection.disconnect()
        }
    }
}
