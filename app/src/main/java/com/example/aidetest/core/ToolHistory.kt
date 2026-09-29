package com.example.aidetest.core

import android.content.Context
import org.json.JSONArray

/** Lightweight recent-tool history. Keeps the most recent 20 tool IDs. */
class ToolHistory(context: Context) {
    private val prefs = context.getSharedPreferences("mytools_tool_history", Context.MODE_PRIVATE)

    fun record(id: String) {
        val current = ids().toMutableList()
        current.remove(id)
        current.add(0, id)
        val trimmed = current.take(MAX_ITEMS)
        prefs.edit().putString(KEY_HISTORY, JSONArray(trimmed).toString()).apply()
    }

    fun ids(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) { for (i in 0 until array.length()) add(array.getString(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() = prefs.edit().remove(KEY_HISTORY).apply()

    companion object {
        private const val KEY_HISTORY = "recent_tools"
        private const val MAX_ITEMS = 20
    }
}
