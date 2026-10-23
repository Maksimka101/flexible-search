package com.zemlianikin.flexiblesearch.plugins.converter.core

import kotlin.time.Instant

/** Только хранение счётчиков, без логики счёта. */
interface CurrencyUsageStore {
    suspend fun load(): Map<CurrencyCode, CurrencyUsage>

    suspend fun save(changes: Map<CurrencyCode, CurrencyUsage>)
}

/**
 * score на момент [updatedAt]; распад ленивый, считается при чтении и
 * записи.
 */
data class CurrencyUsage(val score: Double, val updatedAt: Instant)
