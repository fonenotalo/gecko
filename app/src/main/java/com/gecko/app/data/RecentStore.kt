package com.gecko.app.data

import android.content.Context
import org.json.JSONArray

/** Small ordered "recently played" store, persisted outside DataStore. */
class RecentStore(context: Context) {

    private val prefs = context.getSharedPreferences("gecko_recent", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }.getOrDefault(emptyList())
    }

    fun add(trackId: String) {
        val updated = (listOf(trackId) + load().filterNot { it == trackId }).take(MAX)
        val array = JSONArray().apply { updated.forEach { put(it) } }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "recent_ids"
        const val MAX = 12
    }
}
