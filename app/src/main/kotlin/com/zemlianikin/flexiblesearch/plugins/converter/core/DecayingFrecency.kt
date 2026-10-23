package com.zemlianikin.flexiblesearch.plugins.converter.core

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Frecency: `score·2^(-Δt/halfLife) + 1` на каждое использование, распад
 * ленивый. Холодный старт: если стор пуст, [seed] записывается со score 1.0
 * и дальше распадается как обычное использование. Рейтинг — seed-валюты из
 * [known] и все, что когда-либо использовали (список пополняется
 * автоматически): по убыванию score, при равенстве порядок seed, затем
 * порядок записи. Остальные [known] в рейтинг не входят, их добавляет
 * только использование. Про выражение и про топ-N не знает: исключения и
 * обрезку делает вызывающий.
 */
class DecayingFrecency(
    private val store: CurrencyUsageStore,
    private val known: List<CurrencyCode>,
    private val seed: List<CurrencyCode>,
    private val halfLife: Duration = 21.days,
) {
    /**
     * [used] — различные валюты выражения, каждая засчитывается с весом 1.
     */
    suspend fun recordUsed(used: Set<CurrencyCode>, now: Instant) {
        val usage = loadSeeded(now)
        store.save(
            used.associateWith {
                CurrencyUsage(scoreAt(usage[it], now) + 1.0, now)
            }
        )
    }

    /** Рейтинг по убыванию score на момент [now]. */
    suspend fun ranking(now: Instant): List<CurrencyCode> {
        val usage = loadSeeded(now)
        val base = (seed.filter { it in known } + usage.keys).distinct()
        return base.sortedByDescending {
            scoreAt(usage[it], now)
        } // sortedBy стабилен
    }

    private suspend fun loadSeeded(
        now: Instant
    ): Map<CurrencyCode, CurrencyUsage> {
        val stored = store.load()
        if (stored.isNotEmpty()) return stored
        // Засев вне [known] не пишем: иначе он попал бы в рейтинг вместе с
        // реально использованными валютами.
        val seeded =
            seed
                .filter { it in known }
                .associateWith { CurrencyUsage(1.0, now) }
        store.save(seeded)
        return seeded
    }

    private fun scoreAt(usage: CurrencyUsage?, now: Instant): Double {
        if (usage == null) return 0.0
        val elapsed =
            (now - usage.updatedAt).inWholeMilliseconds.coerceAtLeast(0)
        return usage.score *
            0.5.pow(elapsed.toDouble() / halfLife.inWholeMilliseconds)
    }
}
