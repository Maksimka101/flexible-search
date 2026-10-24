package com.zemlianikin.flexiblesearch.plugins.converter

import com.zemlianikin.flexiblesearch.plugins.converter.data.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConverterSettingsTest {
    private class MapStore : KeyValueStore {
        val map = mutableMapOf<String, String>()

        override fun all() = map.toMap()

        override fun get(key: String) = map[key]

        override fun put(values: Map<String, String>) {
            map += values
        }
    }

    private val defaults = listOf("ca", "=")

    @Test
    fun defaultsUntilChanged() {
        val store = MapStore()
        assertEquals(
            defaults,
            ConverterSettings(store, defaults).prefixes.value,
        )
        ConverterSettings(store, defaults).setPrefixes(listOf("=", "calc"))
        assertEquals(
            listOf("=", "calc"),
            ConverterSettings(store, defaults).prefixes.value,
        )
    }

    @Test
    fun allPrefixesCanBeRemoved() {
        val store = MapStore()
        ConverterSettings(store, defaults).setPrefixes(emptyList())
        assertEquals(
            emptyList(),
            ConverterSettings(store, defaults).prefixes.value,
        )
    }

    @Test
    fun quickActionIsRemembered() {
        val store = MapStore()
        ConverterSettings(store, defaults).setQuickAction(false)
        assertFalse(ConverterSettings(store, defaults).quickAction.value)
    }

    @Test
    fun prefixValidation() {
        assertNull(prefixError("calc", defaults))
        assertNotNull(prefixError("", defaults))
        assertNotNull(prefixError("a b", defaults))
        assertNotNull(prefixError("CA", defaults))
    }
}
