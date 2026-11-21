package com.scancode.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class ScanResult(
    val value: String,
    val format: String,
    val time: Long = System.currentTimeMillis(),
)

internal class History(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun all() = HistoryJson.decode(prefs.getString(KEY, null))

    fun add(result: ScanResult) =
        write(listOf(result) + all().filterNot { it.value == result.value })

    fun remove(result: ScanResult) = write(all().filterNot { it == result })

    fun clear() = prefs.edit { remove(KEY) }

    private fun write(items: List<ScanResult>) =
        prefs.edit { putString(KEY, HistoryJson.encode(items)) }

    private companion object {
        const val KEY = "items"
    }
}

internal object HistoryJson {
    private const val MAX_ENTRIES = 200

    fun decode(stored: String?): List<ScanResult> {
        val entries = try {
            JSONArray(stored ?: return emptyList())
        } catch (_: JSONException) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until minOf(entries.length(), MAX_ENTRIES)) {
                val entry = entries.optJSONObject(index) ?: continue
                val value = entry.optString("value")
                val format = entry.optString("format")
                val time = entry.optLong("time", -1)
                if (value.isNotBlank() && format.isNotBlank() && time >= 0) {
                    add(ScanResult(value, format, time))
                }
            }
        }
    }

    fun encode(items: List<ScanResult>): String {
        val entries = JSONArray()
        items.take(MAX_ENTRIES).forEach { item ->
            entries.put(JSONObject().put("value", item.value).put("format", item.format).put("time", item.time))
        }
        return entries.toString()
    }
}
