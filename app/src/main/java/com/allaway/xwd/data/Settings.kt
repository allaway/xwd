package com.allaway.xwd.data

import android.content.Context

/** Tiny settings store; currently just the user's Claude API key. */
object Settings {
    private const val PREFS = "xwd_settings"
    private const val KEY_API_KEY = "anthropic_api_key"

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, value.trim())
            .apply()
    }
}
