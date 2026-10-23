package com.zemlianikin.flexiblesearch.plugins.converter.ui

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode

/**
 * Найденная валюта: [name] — название на языке интерфейса, [country] —
 * страна, по которой нашли (если по ней).
 */
class CurrencyMatch(
    val code: CurrencyCode,
    val name: String,
    val country: String?,
)
