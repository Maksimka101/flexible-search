package com.zemlianikin.flexiblesearch.poc.components.core

import android.icu.text.Transliterator
import android.util.Log

/**
 * Как ещё мог быть набран запрос: для [NameMatcher]. Возвращает только
 * варианты, отличные от самого запроса.
 */
fun interface QueryVariants {
    fun of(query: String): List<String>

    companion object {
        /** Только набранный текст. */
        val None = QueryVariants { emptyList() }

        /** Другая раскладка, транслит и транслит другой раскладки. */
        val Default: QueryVariants by lazy {
            LayoutAndTransliteration(IcuTransliteration::of)
        }
    }
}

/**
 * `ntktuhfv` → `телеграм` (забыли переключить раскладку), `теле` → `tele`,
 * `gosus` → `госус` (транслит) и сочетание обоих. [transliterate] отдаёт
 * транслит в другие алфавиты; в тестах — подставной.
 */
class LayoutAndTransliteration(
    private val transliterate: (String) -> List<String>
) : QueryVariants {
    override fun of(query: String): List<String> {
        val swapped = KeyboardLayouts.swap(query)
        return (listOfNotNull(swapped) +
                transliterate(query) +
                swapped?.let(transliterate).orEmpty())
            .filter {
                it.isNotBlank() && !it.equals(query, ignoreCase = true)
            }
            .distinct()
    }
}

/** Русская ЙЦУКЕН ↔ английская QWERTY, клавиша в клавишу. */
object KeyboardLayouts {
    private const val ENGLISH =
        "qwertyuiop[]asdfghjkl;'zxcvbnm,.`QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>~"
    private const val RUSSIAN =
        "йцукенгшщзхъфывапролджэячсмитьбюёЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮЁ"

    /**
     * Строка, набранная в другой раскладке, или null, если в ней нет букв:
     * `12+` так и остаётся `12+`. Направление — по тому, каких букв больше.
     */
    fun swap(text: String): String? {
        val cyrillic = text.count { it in RUSSIAN }
        val latin = text.count { it.isLetter() && it in ENGLISH }
        if (cyrillic == 0 && latin == 0) return null
        val (from, to) =
            if (cyrillic > latin) RUSSIAN to ENGLISH else ENGLISH to RUSSIAN
        return buildString(text.length) {
            for (char in text) {
                val index = from.indexOf(char)
                append(if (index >= 0) to[index] else char)
            }
        }
    }
}

/**
 * Транслит через ICU, встроенный в Android 10+. Кириллица → латиница по BGN
 * (`ю` → `yu`, так `ютуб` находит YouTube), латиница → кириллица — обратным
 * `Latin-Cyrillic`.
 */
private object IcuTransliteration {
    private val toLatin = transliterator("Russian-Latin/BGN")
    private val toCyrillic = transliterator("Latin-Cyrillic")

    fun of(text: String): List<String> {
        val hasCyrillic = text.any {
            Character.UnicodeScript.of(it.code) ==
                Character.UnicodeScript.CYRILLIC
        }
        val hasLatin = text.any { it in 'a'..'z' || it in 'A'..'Z' }
        return listOfNotNull(
            if (hasCyrillic) toLatin?.transliterate(text) else null,
            if (hasLatin) toCyrillic?.transliterate(text) else null,
        )
    }

    private fun transliterator(id: String): Transliterator? =
        try {
            Transliterator.getInstance(id)
        } catch (e: Exception) {
            Log.w("QueryVariants", "No transliterator $id", e)
            null
        }
}
