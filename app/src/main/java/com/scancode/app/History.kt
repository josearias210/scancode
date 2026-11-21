package com.scancode.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

internal data class ScanResult(
    val value: String,
    val format: String,
    val time: Long = System.currentTimeMillis(),
)

internal class History(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun snapshot(): ScanLibrary = synchronized(lock) { read() }

    fun add(result: ScanResult) = update { it.save(result) }

    fun remove(result: ScanResult) = update { it.copy(recent = it.recent.filterNot { scan -> scan == result }) }

    fun clear() = update { it.copy(recent = emptyList()) }

    fun setCollections(result: ScanResult, ids: Set<String>, saveToHistory: Boolean) =
        update { it.setCollections(result, ids, saveToHistory) }

    fun deleteCollection(id: String) = update { it.deleteCollection(id) }

    fun saveCollectionName(name: String, id: String? = null): ScanCollection? = synchronized(lock) {
        val trimmed = name.trim()
        val library = read()
        if (trimmed.isEmpty() || library.collections.any { it.id != id && it.name.equals(trimmed, true) }) {
            return@synchronized null
        }

        val collection = if (id == null) ScanCollection(UUID.randomUUID().toString(), trimmed)
        else library.collections.firstOrNull { it.id == id }?.copy(name = trimmed)
            ?: return@synchronized null
        val updated = if (id == null) library.collections + collection
        else library.collections.map { if (it.id == id) collection else it }
        write(library, library.copy(collections = updated))
        collection
    }

    private fun update(change: (ScanLibrary) -> ScanLibrary) = synchronized(lock) {
        val library = read()
        write(library, change(library))
    }

    private fun read(): ScanLibrary {
        val recent = HistoryJson.decode(prefs.getString(HISTORY_KEY, null))
        return CollectionsJson.decode(recent, prefs.getString(COLLECTIONS_KEY, null))
    }

    private fun write(before: ScanLibrary, after: ScanLibrary) {
        prefs.edit {
            if (before.recent != after.recent) {
                if (after.recent.isEmpty()) remove(HISTORY_KEY)
                else putString(HISTORY_KEY, HistoryJson.encode(after.recent))
            }
            if (before.collections != after.collections || before.collected != after.collected) {
                putString(COLLECTIONS_KEY, CollectionsJson.encode(after))
            }
        }
    }

    private companion object {
        val lock = Any()
        const val HISTORY_KEY = "items"
        const val COLLECTIONS_KEY = "collections_v1"
    }
}

internal object HistoryJson {
    const val MAX_ENTRIES = 200

    fun decode(stored: String?): List<ScanResult> {
        val entries = try {
            JSONArray(stored ?: return emptyList())
        } catch (_: JSONException) {
            return emptyList()
        }
        return decodeArray(entries, MAX_ENTRIES)
    }

    fun encode(items: List<ScanResult>): String = encodeArray(items.take(MAX_ENTRIES)).toString()

    fun decodeArray(entries: JSONArray, limit: Int = entries.length()): List<ScanResult> = buildList {
        for (index in 0 until minOf(entries.length(), limit)) {
            val entry = entries.optJSONObject(index) ?: continue
            val value = entry.optString("value")
            val format = entry.optString("format")
            val time = entry.optLong("time", -1)
            if (value.isNotBlank() && format.isNotBlank() && time >= 0) {
                add(ScanResult(value, format, time))
            }
        }
    }

    fun encodeArray(items: Collection<ScanResult>): JSONArray = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().put("value", item.value).put("format", item.format).put("time", item.time))
        }
    }
}

internal object CollectionsJson {
    fun decode(recent: List<ScanResult>, stored: String?): ScanLibrary {
        val data = try {
            JSONObject(stored ?: return ScanLibrary(recent = recent))
        } catch (_: JSONException) {
            return ScanLibrary(recent = recent)
        }
        val archive = HistoryJson.decodeArray(data.optJSONArray("scans") ?: JSONArray()).associateBy { it.value }
        val entries = data.optJSONArray("collections") ?: JSONArray()
        val collections = buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val id = entry.optString("id")
                val name = entry.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                val values = entry.optJSONArray("values") ?: JSONArray()
                val members = (0 until values.length()).map(values::optString).filterTo(mutableSetOf()) {
                    it in archive
                }
                add(ScanCollection(id, name, members))
            }
        }
        val retained = collections.flatMapTo(mutableSetOf()) { it.entries }
        return ScanLibrary(recent, collections, archive.filterKeys { it in retained })
    }

    fun encode(library: ScanLibrary): String {
        val collections = JSONArray().apply {
            library.collections.forEach { collection ->
                put(JSONObject().put("id", collection.id).put("name", collection.name)
                    .put("values", JSONArray(collection.entries)))
            }
        }
        return JSONObject().put("collections", collections)
            .put("scans", HistoryJson.encodeArray(library.collected.values))
            .toString()
    }
}
