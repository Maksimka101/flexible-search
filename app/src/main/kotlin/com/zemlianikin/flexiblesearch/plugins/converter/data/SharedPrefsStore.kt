package com.zemlianikin.flexiblesearch.plugins.converter.data

import android.content.SharedPreferences

/**
 * [KeyValueStore] на SharedPreferences плагина. Ключи хранятся с [prefix]:
 * в одном файле живут несколько сторов, а [all] видит только свои.
 */
class SharedPrefsStore(
    private val prefs: SharedPreferences,
    private val prefix: String,
) : KeyValueStore {
    override fun all(): Map<String, String> =
        prefs.all
            .mapNotNull { (key, value) ->
                if (!key.startsWith(prefix)) return@mapNotNull null
                value?.let { key.removePrefix(prefix) to it.toString() }
            }
            .toMap()

    override fun get(key: String): String? =
        prefs.all[prefix + key]?.toString()

    override fun put(values: Map<String, String>) {
        prefs
            .edit()
            .apply {
                // remove перед putString: тип старого значения мог быть
                // другим.
                values.forEach { (key, value) ->
                    remove(prefix + key).putString(prefix + key, value)
                }
            }
            .apply()
    }
}
