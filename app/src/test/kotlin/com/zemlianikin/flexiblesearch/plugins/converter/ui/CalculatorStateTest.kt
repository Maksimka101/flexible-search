package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Value
import com.zemlianikin.flexiblesearch.plugins.converter.calc.defaultCalculator
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyUsage
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyUsageStore
import com.zemlianikin.flexiblesearch.plugins.converter.core.DecayingFrecency
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate

/**
 * Состояние экрана: что показано и что засчитано во frecency. Стор в
 * памяти, корутины выполняются на месте.
 */
class CalculatorStateTest {
    private class MemoryStore : CurrencyUsageStore {
        val data = mutableMapOf<CurrencyCode, CurrencyUsage>()
        var saves = 0

        override suspend fun load() = data.toMap()

        override suspend fun save(
            changes: Map<CurrencyCode, CurrencyUsage>
        ) {
            saves++
            data += changes
        }
    }

    private fun codes(vararg c: String) = c.map(::CurrencyCode)

    private fun table(vararg perUsd: Pair<String, String>) =
        RateTable(
            LocalDate(2026, 9, 25),
            perUsd.associate { (code, rate) ->
                CurrencyCode(code) to Num(Decimal(rate))
            },
        )

    private val rates = table("USD" to "1", "EUR" to "0.9", "RUB" to "90")
    private val store = MemoryStore()
    private val frecency =
        DecayingFrecency(
            store,
            codes("USD", "EUR", "RUB"),
            codes("USD", "EUR"),
        )
    private val state =
        CalculatorState(
                defaultCalculator(),
                rates,
                CoroutineScope(Dispatchers.Unconfined),
            )
            .also { it.bind(rates, frecency) }

    /** Набор с системной клавиатуры: по символу в позицию курсора. */
    private fun type(text: String) = text.forEach { c ->
        val field = state.input
        val at = field.selection.start
        state.edit(
            TextFieldValue(
                field.text.substring(0, at) + c + field.text.substring(at),
                TextRange(at + 1),
            )
        )
    }

    private fun shownMoney() = state.shown?.ok?.value as Value.Money

    @Test
    fun `binding loads the ranking`() {
        assertEquals(codes("USD", "EUR"), state.ranking)
    }

    @Test
    fun `invalid input keeps the last valid result, empty input drops it`() {
        type("10 usd")
        assertEquals("10 usd", state.shown?.expression?.trim())
        type(" +")
        assertTrue(state.result !is Calculation.Ok)
        assertEquals("10 usd", state.shown?.expression?.trim())
        state.clear()
        assertNull(state.shown)
    }

    @Test
    fun `a set of currencies is recorded once until the input is cleared`() {
        val before = store.saves
        type("10 rub")
        val once = store.saves - before
        assertTrue(once > 0)
        assertTrue(CurrencyCode("RUB") in state.ranking)
        type(" + 5 rub")
        assertEquals(before + once, store.saves)
        state.clear()
        type("10 rub")
        assertEquals(before + 2 * once, store.saves)
    }

    @Test
    fun `new rates recalculate the result`() {
        type("10 usd to eur")
        assertEquals(0, Decimal("9").compareTo(shownMoney().amount.value))
        state.bind(table("USD" to "1", "EUR" to "0.8"), frecency)
        assertEquals(0, Decimal("8").compareTo(shownMoney().amount.value))
    }

    @Test
    fun `a picked currency is typed into the input and recorded`() {
        state.type("10")
        state.pickCurrency(CurrencyCode("RUB"))
        assertEquals("10 rub ", state.input.text)
        assertEquals(TextRange(7), state.input.selection)
        assertTrue(CurrencyCode("RUB") in store.data)
    }

    @Test
    fun `backspace and field edits go through the same input rules`() {
        type("10+5")
        assertEquals("10 + 5", state.input.text)
        state.backspace()
        state.backspace()
        assertEquals("10", state.input.text)
        state.edit(TextFieldValue("10u", TextRange(3)))
        assertEquals("10 u", state.input.text)
    }

    @Test
    fun `conversions follow the ranking and skip the source and missing rates`() {
        val money = Value.Money(Num(Decimal("10")), CurrencyCode("USD"))
        val rows =
            conversions(money, codes("EUR", "USD", "THB", "RUB"), rates)
                .toList()
        assertEquals(codes("EUR", "RUB"), rows.map { it.currency })
        assertEquals(0, Decimal("900").compareTo(rows[1].amount.value))
    }
}
