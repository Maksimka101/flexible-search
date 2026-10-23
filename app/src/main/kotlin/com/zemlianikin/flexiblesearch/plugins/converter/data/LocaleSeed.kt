package com.zemlianikin.flexiblesearch.plugins.converter.data

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import java.util.Locale

fun localeSeed(): List<CurrencyCode> = localeSeed(Locale.getDefault())

/**
 * Дефолты из локали: валюта, затем USD, EUR. Валюта — явная (`-u-cu-`),
 * иначе страна из региона в настройках телефона (`-u-rg-`, Android 14+),
 * иначе страна самой локали.
 */
fun localeSeed(locale: Locale): List<CurrencyCode> {
    val local =
        try {
            locale.getUnicodeLocaleType("cu")?.uppercase()
                ?: locale.getUnicodeLocaleType("rg")?.take(2)?.let {
                    java.util.Currency.getInstance(Locale("", it))
                        .currencyCode
                }
                ?: java.util.Currency.getInstance(locale).currencyCode
        } catch (_: IllegalArgumentException) {
            null // у локали нет страны
        }
    return listOfNotNull(local, "USD", "EUR").distinct().map(::CurrencyCode)
}
