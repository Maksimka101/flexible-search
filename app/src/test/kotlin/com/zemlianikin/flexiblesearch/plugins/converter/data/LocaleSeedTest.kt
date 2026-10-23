package com.zemlianikin.flexiblesearch.plugins.converter.data

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleSeedTest {
    private fun codes(vararg c: String) = c.map(::CurrencyCode)

    @Test
    fun `locale seed puts country currency first`() {
        assertEquals(
            codes("RUB", "USD", "EUR"),
            localeSeed(Locale.forLanguageTag("ru-RU")),
        )
        assertEquals(codes("USD", "EUR"), localeSeed(Locale.US))
        assertEquals(codes("USD", "EUR"), localeSeed(Locale.ENGLISH))
    }

    @Test
    fun `region from phone settings overrides the language country`() {
        assertEquals(
            codes("PLN", "USD", "EUR"),
            localeSeed(Locale.forLanguageTag("ru-RU-u-rg-plzzzz")),
        )
        assertEquals(
            codes("CHF", "USD", "EUR"),
            localeSeed(Locale.forLanguageTag("ru-RU-u-cu-chf-rg-plzzzz")),
        )
    }
}
