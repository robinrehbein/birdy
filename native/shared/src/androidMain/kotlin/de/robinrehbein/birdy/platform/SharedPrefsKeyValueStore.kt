package de.robinrehbein.birdy.platform

import android.content.Context

/** [KeyValueStore] on a private SharedPreferences file. Writes use commit() to report failures like `save()`. */
class SharedPrefsKeyValueStore(context: Context, name: String = "birdy") : KeyValueStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String): Boolean = runCatching { prefs.edit().putString(key, value).commit() }.getOrDefault(false)
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}
