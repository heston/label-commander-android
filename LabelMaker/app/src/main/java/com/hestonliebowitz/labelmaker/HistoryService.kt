package com.hestonliebowitz.labelmaker

import android.content.Context

const val HISTORY_DELIMITER = "~"
const val MAX_HISTORY_ITEMS = 10

class HistoryService(private val ctx: Context) {
    private val _items : MutableList<String> = emptyList<String>().toMutableList()

    fun getAll(): List<String> {
        _items.clear()
        val prefs = ctx.getSharedPreferences(DEFAULT, Context.MODE_PRIVATE)
        val history = prefs.getString(
            ctx.getString(R.string.pref_history),
            ""
        )
        if (!history.isNullOrEmpty()) {
            _items.addAll(history.split(HISTORY_DELIMITER))
        }
        return _items.toList()
    }

    fun save(value: String) {
        // If item already exists, remove it
        val idx = _items.indexOf(value)
        if (idx > -1) {
            _items.removeAt(idx)
        }

        // Add item to front of list
        _items.add(0, value)

        // Ensure list of favorites never exceeds MAX_HISTORY_ITEMS in length
        if (_items.size > MAX_HISTORY_ITEMS) {
            _items.subList(MAX_HISTORY_ITEMS, _items.size).clear()
        }

        // Serialize list into a string and save it
        val newItems = _items.joinToString(HISTORY_DELIMITER)
        val prefs = ctx.getSharedPreferences(DEFAULT, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(
            ctx.getString(R.string.pref_history),
            newItems
        )
        editor.apply()
    }

    fun deleteAll() {
        val prefs = ctx.getSharedPreferences(DEFAULT, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(
            ctx.getString(R.string.pref_history),
            ""
        )
        editor.apply()
    }
}
