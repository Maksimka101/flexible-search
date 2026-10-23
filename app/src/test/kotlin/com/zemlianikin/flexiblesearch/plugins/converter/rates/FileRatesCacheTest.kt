package com.zemlianikin.flexiblesearch.plugins.converter.rates

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class FileRatesCacheTest {
    private val root = Files.createTempDirectory("rates").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun rates(
        fetchedAt: Long = 1_700_000_000_123,
        vararg pairs: Pair<String, String>,
    ): CachedRates {
        val map = pairs.associate { (c, v) ->
            CurrencyCode(c) to Num(Decimal(v))
        }
        return CachedRates(
            RatesSnapshot(LocalDate(2026, 9, 25), map),
            Instant.fromEpochMilliseconds(fetchedAt),
        )
    }

    private fun sample() =
        rates(
            1_700_000_000_123,
            "USD" to "1",
            "EUR" to "0.86",
            "RUB" to "82.5",
            "BTC" to "0.0000094371",
            "SHIB" to "1E-10",
            "IDR" to "16321.123456789012345678",
        )

    @Test
    fun `no file gives null`() = runBlocking {
        assertNull(FileRatesCache(File(root, "rates.txt")).load())
    }

    @Test
    fun `round trip keeps precision date time and order`() = runBlocking {
        val cache = FileRatesCache(File(root, "rates.txt"))
        val src = sample()
        cache.save(src)
        val back = assertNotNull(cache.load())
        assertEquals(src.fetchedAt, back.fetchedAt)
        assertEquals(src.snapshot.date, back.snapshot.date)
        assertEquals(src.snapshot.currencies, back.snapshot.currencies)
        for ((code, num) in src.snapshot.perBase) {
            // Decimal.equals учитывает scale: сравниваем строго, без
            // compareTo.
            assertEquals(
                num.value,
                back.snapshot.perBase.getValue(code).value,
            )
        }
    }

    @Test
    fun `save overwrites atomically and leaves no temp file`() =
        runBlocking {
            val file = File(root, "rates.txt")
            val cache = FileRatesCache(file)
            cache.save(sample())
            val second = rates(5, "USD" to "1", "GBP" to "0.75")
            cache.save(second)
            val back = cache.load()!!
            assertEquals(Instant.fromEpochMilliseconds(5), back.fetchedAt)
            assertEquals(
                listOf(CurrencyCode("USD"), CurrencyCode("GBP")),
                back.snapshot.currencies,
            )
            assertEquals(listOf("rates.txt"), root.list()!!.toList())
        }

    @Test
    fun `save creates missing parent dirs`() =
        runBlocking<Unit> {
            val cache = FileRatesCache(File(root, "a/b/rates.txt"))
            cache.save(sample())
            assertNotNull(cache.load())
        }

    private fun assertBroken(content: String) = runBlocking {
        val file = File(root, "rates.txt")
        file.writeText(content)
        assertNull(FileRatesCache(file).load(), "content: $content")
        assertTrue(file.exists(), "file must stay")
        assertEquals(content, file.readText())
    }

    @Test fun `empty file is broken`() = assertBroken("")

    @Test
    fun `unknown version is broken`() =
        assertBroken("v2\n1700000000000\n2026-09-25\nUSD 1\n")

    @Test
    fun `no version line is broken`() =
        assertBroken("1700000000000\n2026-09-25\nUSD 1\n")

    @Test
    fun `empty table is broken`() =
        assertBroken("v1\n1700000000000\n2026-09-25\n")

    @Test
    fun `bad time is broken`() =
        assertBroken("v1\nabc\n2026-09-25\nUSD 1\n")

    @Test
    fun `bad date is broken`() =
        assertBroken("v1\n1700000000000\n25.09.2026\nUSD 1\n")

    @Test
    fun `bad value is broken`() =
        assertBroken("v1\n1700000000000\n2026-09-25\nUSD 1\nEUR abc\n")

    @Test
    fun `line without value is broken`() =
        assertBroken("v1\n1700000000000\n2026-09-25\nUSD 1\nEUR\n")

    @Test
    fun `blank line in table is broken`() =
        assertBroken("v1\n1700000000000\n2026-09-25\nUSD 1\n\nEUR 2\n")

    @Test
    fun `binary garbage is broken`() = runBlocking {
        val file = File(root, "rates.txt")
        file.writeBytes(byteArrayOf(0, -1, -2, 10, 0x7f, 1))
        assertNull(FileRatesCache(file).load())
        assertTrue(file.exists())
    }

    @Test
    fun `directory instead of file gives null`() = runBlocking {
        val dir = File(root, "rates.txt").also { it.mkdir() }
        assertNull(FileRatesCache(dir).load())
    }
}
