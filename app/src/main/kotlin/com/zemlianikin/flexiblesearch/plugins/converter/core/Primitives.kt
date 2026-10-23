package com.zemlianikin.flexiblesearch.plugins.converter.core

import kotlin.jvm.JvmInline

/** Десятичное число без потери точности. Округляется только при выводе. */
@JvmInline value class Num(val value: Decimal)

/** Код валюты в верхнем регистре: USD, EUR, BTC. */
@JvmInline value class CurrencyCode(val code: String)
