package com.zemlianikin.flexiblesearch.plugins.converter.rates

import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

class FawazRatesProviderTest {
    private val ok = """{"date":"2026-09-25","usd":{"eur":0.86}}"""

    @Test
    fun `основной работает - запасной не трогаем`() = runBlocking {
        val calls = mutableListOf<String>()
        val p =
            FawazRatesProvider(
                listOf("a", "b"),
                get = {
                    calls += it
                    ok
                },
            )
        val s = p.fetch()
        assertEquals(
            Decimal("0.86"),
            s.perBase.values.first { it.value != Decimal.ONE }.value,
        )
        assertEquals(listOf("a"), calls)
    }

    @Test
    fun `основной упал - берётся запасной`() = runBlocking {
        val calls = mutableListOf<String>()
        val p =
            FawazRatesProvider(listOf("a", "b")) { url ->
                calls += url
                if (url == "a") throw IOException("нет сети") else ok
            }
        p.fetch()
        assertEquals(listOf("a", "b"), calls)
    }

    @Test
    fun `основной вернул мусор - берётся запасной`() = runBlocking {
        val p =
            FawazRatesProvider(listOf("a", "b")) { url ->
                if (url == "a") "<html>" else ok
            }
        p.fetch()
        Unit
    }

    @Test
    fun `оба упали - исключение последней ошибки`() {
        val last = IOException("вторая")
        val p =
            FawazRatesProvider(listOf("a", "b")) { url ->
                throw if (url == "a") IOException("первая") else last
            }
        val e = assertFailsWith<IOException> { runBlocking { p.fetch() } }
        assertSame(last, e)
    }

    @Test
    fun `URL по умолчанию - основной и запасной`() = runBlocking {
        val calls = mutableListOf<String>()
        // конструктор без urls: порядок проверяем через подмену get
        val p =
            FawazRatesProvider(
                get = {
                    calls += it
                    throw IOException("x")
                }
            )
        assertFailsWith<IOException> { runBlocking { p.fetch() } }
        assertEquals(listOf(FAWAZ_PRIMARY_URL, FAWAZ_FALLBACK_URL), calls)
    }
}
