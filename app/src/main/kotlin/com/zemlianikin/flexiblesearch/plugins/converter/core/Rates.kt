package com.zemlianikin.flexiblesearch.plugins.converter.core

import kotlinx.datetime.LocalDate

/**
 * Снимок курсов на дату. perBase — сколько единиц валюты дают за 1 базовую,
 * сама база лежит в таблице с курсом 1. Кросс-курс считается через базу.
 */
class RateTable(
    val date: LocalDate,
    private val perBase: Map<CurrencyCode, Num>,
) {
    /** Сколько [to] за 1 [from]; null — курса нет. */
    fun rate(from: CurrencyCode, to: CurrencyCode): Num? {
        val f = perBase[from] ?: return null
        val t = perBase[to] ?: return null
        return Num(t.value / f.value)
    }
}
