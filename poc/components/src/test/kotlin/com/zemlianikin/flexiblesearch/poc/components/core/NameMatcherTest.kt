package com.zemlianikin.flexiblesearch.poc.components.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NameMatcherTest {
    private fun ranked(query: String, vararg texts: String) =
        NameMatcher.rank(query, texts.toList()) { it }.map { it.value }

    @Test
    fun charactersInOrderNotNecessarilyAdjacent() {
        assertEquals(
            listOf(0, 4),
            NameMatcher.match("tg", "Telegram")?.positions,
        )
        assertNull(NameMatcher.match("gt", "Telegram"))
        assertNull(NameMatcher.match("xyz", "Telegram"))
        assertNull(NameMatcher.match("", "Telegram"))
    }

    @Test
    fun wordStartsAndConsecutiveCharactersRankHigher() {
        assertEquals(
            listOf("Telegram", "Hotel"),
            ranked("tel", "Hotel", "Telegram", "Settings"),
        )
        assertEquals(
            listOf("Gmail", "Google Maps"),
            ranked("gm", "Google Maps", "Gmail", "Settings"),
        )
        // P и M — начала слов в первом, но не во втором.
        assertEquals(
            listOf("Google Play Music", "Spotify Premium"),
            ranked("pm", "Spotify Premium", "Google Play Music"),
        )
    }

    @Test
    fun shortestWindowIsHighlighted() {
        // Первая «r» в «report» дальше от «p», чем вторая.
        assertEquals(
            listOf(4, 5),
            NameMatcher.match("rt", "report")?.positions,
        )
    }

    @Test
    fun queryWordsMatchInAnyOrder() {
        assertNotNull(NameMatcher.match("maps google", "Google Maps"))
        assertNull(NameMatcher.match("maps yandex", "Google Maps"))
    }

    @Test
    fun caseDiacriticsAndYoAreIgnored() {
        assertNotNull(NameMatcher.match("ежик", "Ёжик в тумане"))
        assertNotNull(NameMatcher.match("cafe", "Café"))
        assertEquals(
            listOf(0, 1),
            NameMatcher.match("ДО", "договор")?.positions,
        )
    }

    @Test
    fun equalScoresPreferShorterText() {
        assertEquals(
            listOf("report.pdf", "report-q3.pdf"),
            ranked("report", "report-q3.pdf", "report.pdf"),
        )
    }

    @Test
    fun wrongKeyboardLayoutStillMatches() {
        assertEquals(
            listOf(0, 1, 2, 3),
            NameMatcher.match("ntkt", "Телеграм")?.positions,
        )
        assertNotNull(NameMatcher.match("еудуп", "Telegram"))
        assertNull(
            NameMatcher.match(
                "ntkt",
                "Телеграм",
                variants = QueryVariants.None,
            )
        )
    }

    @Test
    fun transliterationMatchesOtherAlphabet() {
        val variants = LayoutAndTransliteration {
            if (it == "теле") listOf("tele") else emptyList()
        }
        assertEquals(
            listOf(0, 1, 2, 3),
            NameMatcher.match("теле", "Telegram", variants)?.positions,
        )
    }

    @Test
    fun typedQueryBeatsItsVariant() {
        val typed = NameMatcher.match("tele", "Telegram")!!.score
        val swapped = NameMatcher.match("еуду", "Telegram")!!.score
        assertTrue(swapped < typed)
    }
}
