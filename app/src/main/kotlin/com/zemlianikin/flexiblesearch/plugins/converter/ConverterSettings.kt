package com.zemlianikin.flexiblesearch.plugins.converter

import com.zemlianikin.flexiblesearch.plugins.converter.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Настройки калькулятора: кнопка в ряду и префиксы. Префиксов может не быть
 * совсем: карточка в общем поиске и кнопка в ряду работают и без них.
 */
internal class ConverterSettings(
    private val store: KeyValueStore,
    defaultPrefixes: List<String>,
) {
    // Префиксы без пробелов внутри, поэтому хранятся через пробел; пустая
    // строка — всё удалено, а не «по умолчанию».
    private val prefixState =
        MutableStateFlow(
            store[PREFIXES]?.split(' ')?.filter { it.isNotEmpty() }
                ?: defaultPrefixes
        )
    private val quickActionState =
        MutableStateFlow(store[QUICK_ACTION] != "false")

    /** Первый вставляет кнопка в ряду. */
    val prefixes: StateFlow<List<String>> = prefixState

    val quickAction: StateFlow<Boolean> = quickActionState

    fun setPrefixes(prefixes: List<String>) {
        store[PREFIXES] = prefixes.joinToString(" ")
        prefixState.value = prefixes
    }

    fun setQuickAction(visible: Boolean) {
        store[QUICK_ACTION] = visible.toString()
        quickActionState.value = visible
    }

    private companion object {
        const val PREFIXES = "prefixes"
        const val QUICK_ACTION = "quickAction"
    }
}

/**
 * Почему [input] нельзя добавить к [prefixes], или `null`. Префикс
 * сравнивается без регистра, как в строке поиска.
 */
internal fun prefixError(input: String, prefixes: List<String>): String? =
    when {
        input.isEmpty() -> "Type a prefix"
        input.any { it.isWhitespace() } -> "No spaces"
        prefixes.any { it.equals(input, ignoreCase = true) } ->
            "Already added"
        else -> null
    }
