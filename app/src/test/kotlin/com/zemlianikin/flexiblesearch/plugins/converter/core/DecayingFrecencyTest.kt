package com.zemlianikin.flexiblesearch.plugins.converter.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Стор в памяти не приостанавливается, поэтому корутина завершается
 * синхронно — без kotlinx.coroutines.
 */
private fun <T> runBlocking(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) { result = it }
    )
    return result!!.getOrThrow()
}

class DecayingFrecencyTest {
    private class MemoryStore : CurrencyUsageStore {
        val data = mutableMapOf<CurrencyCode, CurrencyUsage>()

        override suspend fun load() = data.toMap()

        override suspend fun save(
            changes: Map<CurrencyCode, CurrencyUsage>
        ) {
            data += changes
        }
    }

    private fun codes(vararg c: String) = c.map(::CurrencyCode)

    private val t0 = Instant.parse("2026-09-25T12:00:00Z")
    private val known = codes("USD", "EUR", "RUB", "JPY")

    private fun frecency(
        seed: List<CurrencyCode> = codes("USD", "EUR"),
        store: CurrencyUsageStore = MemoryStore(),
    ) = DecayingFrecency(store, known, seed)

    @Test
    fun `cold start ranks only the seed`() = runBlocking {
        assertEquals(codes("USD", "EUR"), frecency().ranking(t0))
    }

    @Test
    fun `seed outside known list is ignored`() = runBlocking {
        assertEquals(
            codes("EUR", "USD"),
            frecency(seed = codes("EUR", "CHF", "USD")).ranking(t0),
        )
    }

    @Test
    fun `one use equals seed weight and second use overtakes it`() =
        runBlocking {
            val f = frecency()
            f.recordUsed(setOf(CurrencyCode("RUB")), t0)
            assertEquals(codes("USD", "EUR", "RUB"), f.ranking(t0))
            f.recordUsed(setOf(CurrencyCode("RUB")), t0)
            assertEquals(codes("RUB", "USD", "EUR"), f.ranking(t0))
        }

    @Test
    fun `score halves after half life`() = runBlocking {
        val store = MemoryStore()
        val f = frecency(store = store)
        val later = t0.plus(21.days)
        repeat(2) {
            f.recordUsed(setOf(CurrencyCode("RUB")), t0)
        } // RUB = 2.0 → 1.0 через полураспад
        repeat(3) {
            f.recordUsed(setOf(CurrencyCode("JPY")), later)
        } // JPY = 3.0, seed USD/EUR = 0.5
        assertEquals(codes("JPY", "RUB", "USD", "EUR"), f.ranking(later))
        assertEquals(
            3.0,
            store.data.getValue(CurrencyCode("JPY")).score,
            1e-9,
        )
    }

    @Test
    fun `repeated use accumulates`() = runBlocking {
        val store = MemoryStore()
        val f = frecency(store = store)
        repeat(3) { f.recordUsed(setOf(CurrencyCode("JPY")), t0) }
        assertEquals(
            3.0,
            store.data.getValue(CurrencyCode("JPY")).score,
            1e-9,
        )
        assertEquals(CurrencyCode("JPY"), f.ranking(t0).first())
    }

    @Test
    fun `currency outside the known list joins the ranking once it is used`() =
        runBlocking {
            val f = frecency()
            assertEquals(codes("USD", "EUR"), f.ranking(t0))
            f.recordUsed(setOf(CurrencyCode("SEK")), t0)
            assertEquals(codes("USD", "EUR", "SEK"), f.ranking(t0))
            f.recordUsed(setOf(CurrencyCode("SEK")), t0)
            assertEquals(codes("SEK", "USD", "EUR"), f.ranking(t0))
        }
}
