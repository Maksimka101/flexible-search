package com.zemlianikin.flexiblesearch.plugins.converter.rates

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class CachedRatesRepositoryTest {

    private class FakeClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeProvider(var action: suspend (Int) -> RatesSnapshot) :
        RatesProvider {
        var calls = 0

        override suspend fun fetch(): RatesSnapshot = action(++calls)
    }

    private class FakeCache(var stored: CachedRates? = null) : RatesCache {
        var loads = 0
        var saves = 0
        var loadError: Exception? = null
        var saveError: Exception? = null

        override suspend fun load(): CachedRates? {
            loads++
            loadError?.let { throw it }
            return stored
        }

        override suspend fun save(rates: CachedRates) {
            saves++
            saveError?.let { throw it }
            stored = rates
        }
    }

    private val t0 = Instant.parse("2026-09-25T12:00:00Z")
    private val clock = FakeClock(t0)

    private fun snap(rub: String = "82.5") =
        RatesSnapshot(
            LocalDate(2026, 9, 25),
            mapOf(
                CurrencyCode("USD") to Num(Decimal.ONE),
                CurrencyCode("RUB") to Num(Decimal(rub)),
            ),
        )

    private fun cached(age: Duration, rub: String = "70") =
        CachedRates(snap(rub), t0.minus(age))

    private fun provider(rub: String = "82.5") = FakeProvider { snap(rub) }

    private fun failing() = FakeProvider { throw IOException("no network") }

    private fun repo(p: RatesProvider, c: RatesCache) =
        CachedRatesRepository(p, c, clock)

    @Test
    fun `initial state is refreshing without data`() {
        val r = repo(provider(), FakeCache())
        assertEquals(
            RatesState(null, refreshing = true, failed = false),
            r.state.value,
        )
    }

    @Test
    fun `no cache loads and saves`() = runBlocking {
        val p = provider()
        val c = FakeCache()
        val r = repo(p, c)
        r.refreshIfStale()
        assertEquals(1, p.calls)
        assertEquals(1, c.saves)
        val s = r.state.value
        assertFalse(s.refreshing)
        assertFalse(s.failed)
        assertEquals(t0, s.cached!!.fetchedAt)
        assertSame(s.cached, c.stored)
    }

    @Test
    fun `fresh cache is used without fetch`() = runBlocking {
        val p = provider()
        val stored = cached(1.hours)
        val r = repo(p, FakeCache(stored))
        r.refreshIfStale()
        assertEquals(0, p.calls)
        assertEquals(
            RatesState(stored, refreshing = false, failed = false),
            r.state.value,
        )
    }

    @Test
    fun `cache exactly maxAge old is still fresh`() = runBlocking {
        val p = provider()
        val r = repo(p, FakeCache(cached(24.hours)))
        r.refreshIfStale()
        assertEquals(0, p.calls)
    }

    @Test
    fun `stale cache is refreshed`() = runBlocking {
        val p = provider("99")
        val c = FakeCache(cached(25.hours))
        val r = repo(p, c)
        r.refreshIfStale()
        assertEquals(1, p.calls)
        assertEquals(t0, r.state.value.cached!!.fetchedAt)
        assertEquals(
            Decimal("99"),
            r.state.value.cached!!
                .snapshot
                .perBase
                .getValue(CurrencyCode("RUB"))
                .value,
        )
    }

    @Test
    fun `cache from the future is treated as stale`() = runBlocking {
        val p = provider()
        val r = repo(p, FakeCache(cached((-3).hours)))
        r.refreshIfStale()
        assertEquals(1, p.calls)
    }

    @Test
    fun `custom maxAge and clock are respected`() = runBlocking {
        val p = provider()
        val c = FakeCache(cached(10.minutes))
        val r = CachedRatesRepository(p, c, clock, 5.minutes)
        r.refreshIfStale()
        assertEquals(1, p.calls)
        clock.now = t0.plus(4.minutes)
        r.refreshIfStale()
        assertEquals(1, p.calls)
        clock.now = t0.plus(6.minutes)
        r.refreshIfStale()
        assertEquals(2, p.calls)
        assertEquals(clock.now, r.state.value.cached!!.fetchedAt)
    }

    @Test
    fun `network failure keeps old cache and sets failed`() = runBlocking {
        val stored = cached(30.hours)
        val c = FakeCache(stored)
        val r = repo(failing(), c)
        r.refreshIfStale()
        assertEquals(
            RatesState(stored, refreshing = false, failed = true),
            r.state.value,
        )
        assertEquals(0, c.saves)
    }

    @Test
    fun `network failure without cache`() = runBlocking {
        val r = repo(failing(), FakeCache())
        r.refreshIfStale()
        assertEquals(
            RatesState(null, refreshing = false, failed = true),
            r.state.value,
        )
    }

    @Test
    fun `success after failure clears failed`() = runBlocking {
        val p = failing()
        val r = repo(p, FakeCache())
        r.refreshIfStale()
        assertTrue(r.state.value.failed)
        p.action = { snap() }
        r.refresh()
        assertFalse(r.state.value.failed)
        assertFalse(r.state.value.refreshing)
        assertEquals(t0, r.state.value.cached!!.fetchedAt)
    }

    @Test
    fun `save error does not lose fresh rates`() = runBlocking {
        val c = FakeCache().also { it.saveError = IOException("disk full") }
        val r = repo(provider(), c)
        r.refreshIfStale()
        val s = r.state.value
        assertEquals(1, c.saves)
        assertFalse(s.failed)
        assertFalse(s.refreshing)
        assertEquals(t0, s.cached!!.fetchedAt)
    }

    @Test
    fun `load error is treated as no cache`() = runBlocking {
        val c = FakeCache().also { it.loadError = IOException("corrupt") }
        val p = provider()
        val r = repo(p, c)
        r.refreshIfStale()
        assertEquals(1, p.calls)
        assertEquals(t0, r.state.value.cached!!.fetchedAt)
    }

    @Test
    fun `refresh always fetches`() = runBlocking {
        val p = provider()
        val r = repo(p, FakeCache(cached(1.minutes)))
        r.refresh()
        assertEquals(1, p.calls)
        r.refresh()
        assertEquals(2, p.calls)
    }

    @Test
    fun `refresh before refreshIfStale keeps cached on failure`() =
        runBlocking {
            val stored = cached(1.hours)
            val r = repo(failing(), FakeCache(stored))
            r.refresh()
            assertEquals(
                RatesState(stored, refreshing = false, failed = true),
                r.state.value,
            )
        }

    @Test
    fun `repeated refreshIfStale does not reread cache`() = runBlocking {
        val c = FakeCache(cached(1.hours))
        val p = provider()
        val r = repo(p, c)
        r.refreshIfStale()
        r.refreshIfStale()
        r.refresh()
        r.refreshIfStale()
        assertEquals(1, c.loads)
        assertEquals(1, p.calls)
    }

    @Test
    fun `first refreshIfStale after empty cache does not reread it`() =
        runBlocking {
            val c = FakeCache()
            val r = repo(failing(), c)
            r.refreshIfStale()
            r.refreshIfStale()
            assertEquals(1, c.loads)
        }

    @Test
    fun `refreshing is true while fetching`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val p = FakeProvider {
            started.complete(Unit)
            gate.await()
            snap()
        }
        val r = repo(p, FakeCache(cached(30.hours)))
        val job = launch { r.refreshIfStale() }
        started.await()
        assertTrue(r.state.value.refreshing)
        assertFalse(r.state.value.cached == null)
        gate.complete(Unit)
        job.join()
        assertFalse(r.state.value.refreshing)
    }

    @Test
    fun `concurrent refreshIfStale fetches once`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val p = FakeProvider {
            gate.await()
            snap()
        }
        val c = FakeCache()
        val r = repo(p, c)
        val a = launch { r.refreshIfStale() }
        val b = launch { r.refreshIfStale() }
        gate.complete(Unit)
        a.join()
        b.join()
        assertEquals(1, p.calls)
        assertEquals(1, c.loads)
    }

    @Test
    fun `cancellation from provider is propagated`() = runBlocking {
        val p = FakeProvider { throw CancellationException("cancelled") }
        val r = repo(p, FakeCache())
        try {
            r.refreshIfStale()
            fail("expected CancellationException")
        } catch (_: CancellationException) {}
        assertFalse(r.state.value.failed)
        assertNull(r.state.value.cached)
    }

    @Test
    fun `cancellation keeps repository usable`() = runBlocking {
        val stored = cached(30.hours)
        val p = FakeProvider { n ->
            if (n == 1) throw CancellationException("x") else snap()
        }
        val r = repo(p, FakeCache(stored))
        try {
            r.refresh()
            fail("expected CancellationException")
        } catch (_: CancellationException) {}
        assertFalse(r.state.value.refreshing)
        assertFalse(r.state.value.failed)
        r.refresh()
        assertEquals(t0, r.state.value.cached!!.fetchedAt)
    }

    @Test
    fun `cancellation from cache save is propagated`() = runBlocking {
        val c =
            FakeCache().also { it.saveError = CancellationException("x") }
        val r = repo(provider(), c)
        try {
            r.refreshIfStale()
            fail("expected CancellationException")
        } catch (_: CancellationException) {}
    }
}
