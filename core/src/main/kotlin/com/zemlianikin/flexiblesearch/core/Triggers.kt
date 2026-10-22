package com.zemlianikin.flexiblesearch.core

import android.util.Log

/**
 * Префикс-команда: `calc 2+2`, `=2+2`. Пока префикс в строке, запрос
 * получают только провайдеры этого триггера, и даже с пустым остатком. В
 * строке префикс показывается чипом с [label].
 */
class Trigger
internal constructor(
    val id: String,
    val label: String,
    val prefixes: List<String>,
    /** Подсказка в поле, пока после префикса ничего не набрано. */
    val placeholder: String?,
    /** Показывается в чипе перед [label]. */
    val icon: ActionIcon? = null,
) {
    /**
     * Строка, которая включает этот триггер: `command("2+2")` → `calc 2+2`.
     */
    fun command(text: String = ""): String {
        val prefix = prefixes.first()
        return prefix + separatorAfter(prefix) + text
    }
}

/**
 * После словесного префикса (`calc`) нужен пробел, иначе `calculator`
 * включал бы калькулятор; символьный (`=`) срабатывает сразу.
 */
internal fun separatorAfter(prefix: String) =
    if (prefix.last().isLetterOrDigit()) " " else ""

/**
 * Строка поиска: активный триггер (чип) и текст после него. Без триггера
 * [text] — вся строка.
 */
data class SearchQuery(val text: String = "", val trigger: Trigger? = null)

/**
 * Находит префикс в начале строки, см. [separatorAfter]. Регистр не важен.
 * При одинаковых префиксах побеждает первый зарегистрированный.
 */
class TriggerParser(private val triggers: List<Trigger>) {
    init {
        val seen = HashMap<String, Trigger>()
        for (trigger in triggers) for (prefix in trigger.prefixes) {
            val owner = seen.putIfAbsent(prefix.lowercase(), trigger)
            if (owner != null) {
                Log.w(
                    TAG,
                    "\"$prefix\" of ${trigger.id} taken by ${owner.id}",
                )
            }
        }
    }

    fun parse(input: String): SearchQuery {
        for (trigger in triggers) for (prefix in trigger.prefixes) {
            val start = prefix + separatorAfter(prefix)
            if (input.startsWith(start, ignoreCase = true)) {
                return SearchQuery(input.substring(start.length), trigger)
            }
        }
        return SearchQuery(input)
    }

    fun byId(id: String?): Trigger? = triggers.find { it.id == id }

    private companion object {
        const val TAG = "TriggerParser"
    }
}
