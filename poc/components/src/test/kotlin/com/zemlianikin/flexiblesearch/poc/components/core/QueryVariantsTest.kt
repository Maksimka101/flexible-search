package com.zemlianikin.flexiblesearch.poc.components.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryVariantsTest {
    @Test
    fun swapsKeyboardLayoutBothWays() {
        assertEquals("телеграм", KeyboardLayouts.swap("ntktuhfv"))
        assertEquals("привет", KeyboardLayouts.swap("ghbdtn"))
        assertEquals("hello", KeyboardLayouts.swap("руддщ"))
        assertEquals("Ёжик", KeyboardLayouts.swap("~;br"))
    }

    @Test
    fun noLettersNoLayoutVariant() {
        assertNull(KeyboardLayouts.swap("12+"))
        assertNull(KeyboardLayouts.swap("(1.5+2)/3"))
        assertEquals(
            emptyList(),
            LayoutAndTransliteration { emptyList() }.of("12+"),
        )
    }

    @Test
    fun combinesLayoutAndTransliterationWithoutDuplicates() {
        val variants = LayoutAndTransliteration { text ->
            mapOf("ntkt" to listOf("нткт"), "теле" to listOf("tele"))[text]
                .orEmpty()
        }
        assertEquals(listOf("теле", "нткт", "tele"), variants.of("ntkt"))
        assertEquals(emptyList(), QueryVariants.None.of("ntkt"))
    }
}
