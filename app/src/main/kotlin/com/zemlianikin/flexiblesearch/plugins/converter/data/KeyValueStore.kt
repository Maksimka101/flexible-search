package com.zemlianikin.flexiblesearch.plugins.converter.data

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyUsage
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyUsageStore
import kotlin.time.Instant

/**
 * Маленькое хранилище настроек «ключ → строка»: SharedPreferences на
 * Android, `java.util.prefs` на desktop.
 */
interface KeyValueStore {
    fun all(): Map<String, String>

    operator fun get(key: String): String?

    fun put(values: Map<String, String>)

    operator fun set(key: String, value: String) = put(mapOf(key to value))
}

/** Стор frecency поверх [KeyValueStore]: `USD` → `score;epochMillis`. */
class KeyValueUsageStore(private val store: KeyValueStore) :
    CurrencyUsageStore {
    override suspend fun load(): Map<CurrencyCode, CurrencyUsage> =
        store
            .all()
            .mapNotNull { (code, raw) ->
                val (score, millis) =
                    raw.split(';').takeIf { it.size == 2 }
                        ?: return@mapNotNull null
                CurrencyCode(code) to
                    CurrencyUsage(
                        score.toDoubleOrNull() ?: return@mapNotNull null,
                        Instant.fromEpochMilliseconds(
                            millis.toLongOrNull() ?: return@mapNotNull null
                        ),
                    )
            }
            .toMap()

    override suspend fun save(changes: Map<CurrencyCode, CurrencyUsage>) {
        store.put(
            changes.entries.associate { (code, usage) ->
                code.code to
                    "${usage.score};${usage.updatedAt.toEpochMilliseconds()}"
            }
        )
    }
}
