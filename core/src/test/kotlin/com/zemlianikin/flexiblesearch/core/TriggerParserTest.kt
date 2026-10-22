package com.zemlianikin.flexiblesearch.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TriggerParserTest {
    private val calc =
        Trigger("calc", "Calculator", listOf("calc", "="), null)
    private val maps = Trigger("maps", "Maps", listOf("maps"), null)
    private val parser = TriggerParser(listOf(calc, maps))

    @Test
    fun wordPrefixNeedsSpace() {
        assertEquals(SearchQuery("2+2", calc), parser.parse("calc 2+2"))
        assertEquals(SearchQuery("", calc), parser.parse("calc "))
        assertEquals(SearchQuery("", calc), parser.parse("Calc "))
        assertEquals(SearchQuery("calc"), parser.parse("calc"))
        assertEquals(SearchQuery("calculator"), parser.parse("calculator"))
    }

    @Test
    fun symbolPrefixWorksWithoutSpace() {
        assertEquals(SearchQuery("2+2", calc), parser.parse("=2+2"))
        assertEquals(SearchQuery("", calc), parser.parse("="))
        assertEquals(SearchQuery(" 2", calc), parser.parse("= 2"))
    }

    @Test
    fun prefixOnlyAtStart() {
        assertEquals(SearchQuery("кофе", maps), parser.parse("maps кофе"))
        assertEquals(SearchQuery("2=2"), parser.parse("2=2"))
        assertEquals(SearchQuery(" calc 2"), parser.parse(" calc 2"))
    }

    @Test
    fun firstRegisteredWinsOnConflict() {
        val other = Trigger("other", "Other", listOf("calc"), null)
        val parser = TriggerParser(listOf(calc, other))
        assertEquals(calc, parser.parse("calc 1").trigger)
    }
}
