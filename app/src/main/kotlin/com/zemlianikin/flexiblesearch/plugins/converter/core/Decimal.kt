package com.zemlianikin.flexiblesearch.plugins.converter.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Единственное место, где логика касается `java.math`. Равенство — как у
 * `BigDecimal`.
 */
class Decimal private constructor(val java: BigDecimal) :
    Comparable<Decimal> {
    constructor(text: String) : this(BigDecimal(text))

    operator fun plus(other: Decimal) = Decimal(java.add(other.java))

    operator fun minus(other: Decimal) = Decimal(java.subtract(other.java))

    operator fun times(other: Decimal) = Decimal(java.multiply(other.java))

    operator fun div(other: Decimal) =
        Decimal(java.divide(other.java, MathContext.DECIMAL128))

    operator fun unaryMinus() = Decimal(java.negate())

    fun abs() = Decimal(java.abs())

    fun signum(): Int = java.signum()

    fun movePointLeft(digits: Int) = Decimal(java.movePointLeft(digits))

    fun rounded(scale: Int) =
        Decimal(java.setScale(scale, RoundingMode.HALF_UP))

    fun stripTrailingZeros() = Decimal(java.stripTrailingZeros())

    fun toPlainString(): String = java.toPlainString()

    override fun compareTo(other: Decimal): Int = java.compareTo(other.java)

    override fun equals(other: Any?): Boolean =
        other is Decimal && java == other.java

    override fun hashCode(): Int = java.hashCode()

    override fun toString(): String = java.toString()

    companion object {
        val ONE = Decimal(BigDecimal.ONE)

        fun of(value: Long) = Decimal(BigDecimal.valueOf(value))

        fun parseOrNull(text: String): Decimal? =
            text.toBigDecimalOrNull()?.let(::Decimal)
    }
}
