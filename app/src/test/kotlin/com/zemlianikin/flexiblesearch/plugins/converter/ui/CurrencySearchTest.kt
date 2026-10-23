package com.zemlianikin.flexiblesearch.plugins.converter.ui

import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Lexicon
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Поиск валюты для кнопки «+»: код, слово словаря, название валюты, страна.
 * Названия — из JDK на en/ru.
 */
class CurrencySearchTest {
    private val directory = CurrencyDirectory()
    private val none = emptyList<CurrencyCode>()

    private fun codes(query: String, ranking: List<CurrencyCode> = none) =
        directory.search(query, ranking).map { it.code.code }

    @Test
    fun `exact code comes first and a code prefix follows`() {
        assertEquals("SEK", codes("sek").first())
        assertTrue(codes("se").containsAll(listOf("SEK")))
        assertEquals("SEK", codes("SEK ").first())
    }

    @Test
    fun `dictionary words and currency names find the currency`() {
        assertEquals("USD", codes("dollar").first())
        assertEquals("USD", codes("доллар").first())
        assertTrue("SEK" in codes("swedish"))
        assertTrue("SEK" in codes("шведская"))
    }

    @Test
    fun `country names find the currency and name the country`() {
        assertTrue("SEK" in codes("sweden"))
        assertTrue("SEK" in codes("швеция"))
        assertTrue("EUR" in codes("germany"))
        assertNotNull(
            directory
                .search("sweden", none)
                .first { it.code.code == "SEK" }
                .country
        )
    }

    @Test
    fun `only currencies the parser understands are offered`() {
        // ISO-валюта, которой нет в словаре разбора, не предлагается: её
        // код во вводе был бы ошибкой.
        assertTrue(codes("zzz").isEmpty())
        val offered = directory.search("s", none, limit = 500)
        assertTrue(offered.isNotEmpty())
        assertTrue(
            offered.all {
                Lexicon.Default.meaning(it.code.code) is
                    Lexicon.Meaning.Currency
            }
        )
    }

    @Test
    fun `ranking orders equal matches and an empty query lists the ranking`() {
        val ranking = listOf("EUR", "USD").map(::CurrencyCode)
        assertEquals(listOf("EUR", "USD"), codes("", ranking))
        // `$` — слово USD; порядок среди совпадений одного уровня задаёт
        // рейтинг
        val kr =
            codes("kr", listOf("NOK", "DKK", "SEK").map(::CurrencyCode))
        assertEquals(
            listOf("NOK", "DKK", "SEK"),
            kr.filter { it in setOf("NOK", "DKK", "SEK") },
        )
    }

    @Test
    fun `country is filled only when the match came from the country`() {
        assertNull(directory.search("sek", none).first().country)
    }
}
