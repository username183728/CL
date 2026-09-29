package com.example.aidetest.core

import android.content.Context

/** Small persistent store for global tool UX: favorites and hidden tools. */
class ToolPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("mytools_tool_preferences", Context.MODE_PRIVATE)

    fun isFavorite(id: String): Boolean = prefs.getStringSet(KEY_FAVORITES, emptySet())?.contains(id) == true

    fun setFavorite(id: String, favorite: Boolean) {
        val set = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toMutableSet()
        if (favorite) set.add(id) else set.remove(id)
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
    }

    fun isHidden(id: String): Boolean = prefs.getStringSet(KEY_HIDDEN, emptySet())?.contains(id) == true

    fun setHidden(id: String, hidden: Boolean) {
        val set = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toMutableSet()
        if (hidden) set.add(id) else set.remove(id)
        prefs.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
    fun hidden(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    companion object {
        private const val KEY_FAVORITES = "favorite_tools"
        private const val KEY_HIDDEN = "hidden_tools"
    }
}
