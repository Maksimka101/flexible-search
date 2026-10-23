package com.zemlianikin.flexiblesearch.plugins.converter.rates

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable
import com.zemlianikin.flexiblesearch.plugins.converter.core.today

/** Мок-курсы: USD, EUR, RUB относительно USD. Цифры выдуманные. */
private val mockPerBase =
    mapOf(
        CurrencyCode("USD") to Num(Decimal.ONE),
        CurrencyCode("EUR") to Num(Decimal("0.86")),
        CurrencyCode("RUB") to Num(Decimal("82.5")),
    )

/** Валюты, для которых в моке есть курс: `RateTable` состав не отдаёт. */
val mockCurrencies: List<CurrencyCode> = mockPerBase.keys.toList()

fun mockRates(): RateTable =
    RateTable(date = today(), perBase = mockPerBase)
