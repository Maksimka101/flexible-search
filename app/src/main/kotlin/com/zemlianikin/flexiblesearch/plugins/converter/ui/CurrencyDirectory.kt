package com.zemlianikin.flexiblesearch.plugins.converter.ui

import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Lexicon
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import java.util.Currency
import java.util.Locale

/**
 * Поиск валюты по коду, слову словаря (`доллар`, `dollar`), названию валюты
 * и названию страны (`швеция` → SEK). Названия — на языке интерфейса,
 * русском и английском. Ищутся только валюты, которые понимает разбор
 * (`Lexicon`): иначе вставленный код был бы ошибкой. Порядок: точный код,
 * начало кода, слово словаря, название валюты, страна; внутри — по [search]
 * `ranking`.
 */
class CurrencyDirectory(
    private val lexicon: Lexicon,
    private val locales: List<Locale> =
        listOf(
                Locale.getDefault(),
                Locale.forLanguageTag("ru"),
                Locale.ENGLISH,
            )
            .distinct(),
) {
    constructor() : this(Lexicon.Default)

    private class Country(val name: String, val keys: List<String>)

    private class Entry(
        val code: CurrencyCode,
        val name: String,
        val nameKeys: List<String>,
        val countries: List<Country>,
    )

    // Строится при первом поиске: перебор стран и названий занимает
    // заметное время.
    private val entries: List<Entry> by lazy { build() }

    private fun build(): List<Entry> {
        val supported =
            lexicon.currenciesWithPrefix("").filter {
                lexicon.meaning(it.code) is Lexicon.Meaning.Currency
            }
        val countries = HashMap<String, MutableList<Country>>()
        for (iso in Locale.getISOCountries()) {
            val region = Locale.Builder().setRegion(iso).build()
            val code =
                runCatching { Currency.getInstance(region)?.currencyCode }
                    .getOrNull() ?: continue
            countries.getOrPut(code) { mutableListOf() } +=
                Country(
                    region.getDisplayCountry(locales.first()),
                    locales.map {
                        region.getDisplayCountry(it).lowercase(it)
                    },
                )
        }
        return supported.map { code ->
            val currency = runCatching {
                Currency.getInstance(code.code)
            }
                .getOrNull()
            val names =
                currency
                    ?.let { c -> locales.map { c.getDisplayName(it) } }
                    .orEmpty()
            Entry(
                code,
                names.firstOrNull() ?: code.code,
                names.map { it.lowercase() },
                countries[code.code].orEmpty(),
            )
        }
    }

    /**
     * Строка [key] начинается с [query] или содержит слово, которое с него
     * начинается.
     */
    private fun matches(key: String, query: String) =
        key.startsWith(query) ||
            key.contains(" $query") ||
            key.contains("($query")

    fun search(
        query: String,
        ranking: List<CurrencyCode>,
        limit: Int = 20,
    ): List<CurrencyMatch> {
        val q = query.trim().lowercase()
        fun rank(code: CurrencyCode) =
            ranking.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it }
        if (q.isEmpty()) {
            val byCode = entries.associateBy { it.code }
            return ranking
                .mapNotNull { byCode[it] }
                .take(limit)
                .map { CurrencyMatch(it.code, it.name, null) }
        }
        val words = lexicon.currenciesWithPrefix(q).toSet()
        class Hit(val score: Int, val entry: Entry, val country: String?)
        return entries
            .mapNotNull { e ->
                val code = e.code.code.lowercase()
                val country =
                    e.countries.firstOrNull { c ->
                        c.keys.any { matches(it, q) }
                    }
                val score =
                    when {
                        code == q -> 0
                        code.startsWith(q) -> 1
                        e.code in words -> 2
                        e.nameKeys.any { matches(it, q) } -> 3
                        country != null -> 4
                        else -> return@mapNotNull null
                    }
                Hit(score, e, country?.name.takeIf { score == 4 })
            }
            .sortedWith(
                compareBy(
                    { it.score },
                    { rank(it.entry.code) },
                    { it.entry.code.code },
                )
            )
            .take(limit)
            .map { CurrencyMatch(it.entry.code, it.entry.name, it.country) }
    }
}
