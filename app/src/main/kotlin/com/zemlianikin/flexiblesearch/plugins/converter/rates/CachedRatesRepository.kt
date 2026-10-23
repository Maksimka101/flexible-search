package com.zemlianikin.flexiblesearch.plugins.converter.rates

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [RatesRepository] поверх [RatesProvider] и [RatesCache].
 *
 * Вызовы сериализуются мьютексом. [refreshIfStale] после захвата мьютекса
 * заново проверяет, свежи ли курсы, поэтому параллельный второй вызов не
 * делает лишнего запроса. Явный [refresh] грузит всегда. Время берём только
 * из [clock]. Сбой сети или кэша уходит в [RatesState.failed] /
 * игнорируется, наружу летит лишь [CancellationException].
 */
class CachedRatesRepository(
    private val provider: RatesProvider,
    private val cache: RatesCache,
    private val clock: Clock = Clock.System,
    private val maxAge: Duration = 24.hours,
) : RatesRepository {

    private val mutex = Mutex()
    private var cacheLoaded = false
    private val _state =
        MutableStateFlow(
            RatesState(cached = null, refreshing = true, failed = false)
        )

    override val state: StateFlow<RatesState> = _state.asStateFlow()

    override suspend fun refreshIfStale() {
        mutex.withLock {
            guarded {
                loadCacheOnce()
                val cached = _state.value.cached
                if (cached == null || isStale(cached)) {
                    fetchAndStore()
                } else {
                    _state.update { it.copy(refreshing = false) }
                }
            }
        }
    }

    override suspend fun refresh() {
        mutex.withLock {
            guarded {
                loadCacheOnce()
                fetchAndStore()
            }
        }
    }

    /**
     * При отмене не оставляем `refreshing = true`, если кэш уже прочитан;
     * до чтения кэша оставляем «в начале».
     */
    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            if (cacheLoaded) _state.update { it.copy(refreshing = false) }
            throw e
        }
    }

    private suspend fun loadCacheOnce() {
        if (cacheLoaded) return
        val loaded =
            try {
                cache.load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        cacheLoaded = true
        _state.update { it.copy(cached = loaded ?: it.cached) }
    }

    private suspend fun fetchAndStore() {
        _state.update { it.copy(refreshing = true) }
        val snapshot =
            try {
                provider.fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(refreshing = false, failed = true) }
                return
            }
        val fresh = CachedRates(snapshot, clock.now())
        // Сбой записи не должен терять свежие курсы: в памяти они уже есть.
        try {
            cache.save(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        _state.value =
            RatesState(cached = fresh, refreshing = false, failed = false)
    }

    /**
     * Курсы «из будущего» (часы переведены назад) тоже считаем устаревшими,
     * иначе они не обновятся никогда.
     */
    private fun isStale(cached: CachedRates): Boolean {
        val age = clock.now() - cached.fetchedAt
        return age.isNegative() || age > maxAge
    }
}
