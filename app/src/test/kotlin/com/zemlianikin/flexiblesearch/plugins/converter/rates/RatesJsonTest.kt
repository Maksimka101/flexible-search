package com.zemlianikin.flexiblesearch.plugins.converter.rates

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class RatesJsonTest {
    private fun RatesSnapshot.rate(code: String) =
        perBase[CurrencyCode(code)]?.value

    @Test
    fun `обычный ответ`() {
        val s =
            parseRates(
                """{"date":"2026-09-25","usd":{"usd":1,"eur":0.86,"rub":82.5}}"""
            )
        assertEquals(LocalDate(2026, 9, 25), s.date)
        assertEquals(Decimal("0.86"), s.rate("EUR"))
        assertEquals(Decimal("82.5"), s.rate("RUB"))
        assertEquals(
            listOf("USD", "EUR", "RUB"),
            s.currencies.map { it.code },
        )
    }

    @Test
    fun `пробелы и переводы строк`() {
        val s =
            parseRates(
                "\n{ \"date\" : \"2026-09-25\" ,\r\n \"usd\" : { \"eur\" : 0.5 } }\n"
            )
        assertEquals(Decimal("0.5"), s.rate("EUR"))
    }

    @Test
    fun `база добавляется с курсом 1 первой`() {
        val s = parseRates("""{"date":"2026-09-25","usd":{"eur":0.86}}""")
        assertEquals(Decimal.ONE, s.rate("USD"))
        assertEquals("USD", s.currencies.first().code)
    }

    @Test
    fun `база из ответа не перезаписывается`() {
        val s =
            parseRates(
                """{"date":"2026-09-25","usd":{"usd":1.0,"eur":0.86}}"""
            )
        assertEquals(Decimal("1.0"), s.rate("USD"))
    }

    @Test
    fun `экспонента`() {
        val s =
            parseRates(
                """{"date":"2026-09-25","usd":{"xyz":1.2e-05,"big":3E+2,"btc":8.7E-6}}"""
            )
        assertEquals(0, Decimal("0.000012").compareTo(s.rate("XYZ")!!))
        assertEquals(0, Decimal("300").compareTo(s.rate("BIG")!!))
        assertEquals(0, Decimal("0.0000087").compareTo(s.rate("BTC")!!))
    }

    @Test
    fun `точность Decimal не теряется`() {
        val text = "0.12345678901234567890123456789"
        val s = parseRates("""{"date":"2026-09-25","usd":{"eur":$text}}""")
        assertEquals(Decimal(text), s.rate("EUR"))
    }

    @Test
    fun `коды переводятся в верхний регистр`() {
        val s =
            parseRates(
                """{"date":"2026-09-25","usd":{"eur":0.86,"1inch":2}}"""
            )
        assertTrue(CurrencyCode("EUR") in s.perBase)
        assertTrue(CurrencyCode("1INCH") in s.perBase)
    }

    @Test
    fun `мусорные значения пропускаются`() {
        val s =
            parseRates(
                """{"date":"2026-09-25","usd":{
                "zero":0,"neg":-1.5,"str":"12","nul":null,"t":true,"f":false,
                "arr":[1,2],"obj":{"a":1},"huge":1e999999999999,
                "bad code":5,"я":5,"a-b":5,"":5,
                "eur":0.86}}"""
            )
        assertEquals(
            setOf("USD", "EUR"),
            s.currencies.map { it.code }.toSet(),
        )
    }

    @Test
    fun `неизвестные вложенные поля пропускаются`() {
        val s =
            parseRates(
                """{"meta":{"a":[1,{"b":"}\"{"},null,true,-1.5e3],"c":{}},"date":"2026-09-25",
               "list":[],"usd":{"eur":0.86},"tail":[[[]]]}"""
            )
        assertEquals(Decimal("0.86"), s.rate("EUR"))
    }

    @Test
    fun `escape в ключах и значениях`() {
        val s =
            parseRates(
                """{"date":"2026-09-25","note":"a\nb\\\/","usd":{"eur":0.86}}"""
            )
        assertEquals(Decimal("0.86"), s.rate("EUR"))
    }

    @Test
    fun `дубликат кода - побеждает последний`() {
        val s =
            parseRates("""{"date":"2026-09-25","usd":{"eur":1,"EUR":2}}""")
        assertEquals(Decimal("2"), s.rate("EUR"))
    }

    @Test
    fun `пустой usd - только база`() {
        val s = parseRates("""{"date":"2026-09-25","usd":{}}""")
        assertEquals(listOf("USD"), s.currencies.map { it.code })
    }

    @Test
    fun `toTable считает кросс-курс`() {
        val t =
            parseRates(
                    """{"date":"2026-09-25","usd":{"eur":0.5,"rub":100}}"""
                )
                .toTable()
        assertEquals(
            0,
            Decimal("200")
                .compareTo(
                    t.rate(CurrencyCode("EUR"), CurrencyCode("RUB"))!!.value
                ),
        )
    }

    private fun bad(json: String, contains: String? = null) {
        val e =
            assertFailsWith<IllegalArgumentException> { parseRates(json) }
        if (contains != null)
            assertTrue(e.message!!.contains(contains), e.message)
    }

    @Test fun `нет date`() = bad("""{"usd":{"eur":1}}""", "date")

    @Test fun `нет usd`() = bad("""{"date":"2026-09-25"}""", "usd")

    @Test
    fun `неверная дата`() =
        bad("""{"date":"25.09.2026","usd":{}}""", "date")

    @Test fun `date не строка`() = bad("""{"date":20260925,"usd":{}}""")

    @Test fun `usd не объект`() = bad("""{"date":"2026-09-25","usd":[1]}""")

    @Test fun `пустая строка`() = bad("")

    @Test fun `не объект`() = bad("[1,2]")

    @Test
    fun `оборванный JSON`() =
        bad("""{"date":"2026-09-25","usd":{"eur":0.8""")

    @Test fun `оборванная строка`() = bad("""{"date":"2026-09-25""")

    @Test fun `нет запятой`() = bad("""{"date":"2026-09-25" "usd":{}}""")

    @Test
    fun `лишние данные после JSON`() =
        bad("""{"date":"2026-09-25","usd":{}} x""")

    @Test fun `HTML вместо JSON`() = bad("<html>502 Bad Gateway</html>")

    @Test
    fun `неверное число`() =
        bad("""{"date":"2026-09-25","usd":{"eur":1.}}""")

    @Test
    fun `ведущие нули в числе`() =
        bad("""{"date":"2026-09-25","usd":{"eur":01}}""")

    @Test
    fun `неверный escape`() =
        bad("""{"date":"2026-09-25","x":"\q","usd":{}}""")

    @Test
    fun `слишком глубокая вложенность не роняет стек`() {
        val deep = "[".repeat(10_000) + "]".repeat(10_000)
        bad("""{"date":"2026-09-25","x":$deep,"usd":{}}""", "вложенность")
    }
}
