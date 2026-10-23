package com.zemlianikin.flexiblesearch.plugins.converter.rates

/** Основной URL: CDN jsDelivr. */
const val FAWAZ_PRIMARY_URL =
    "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"

/** Запасной URL: зеркало того же источника на Cloudflare Pages. */
const val FAWAZ_FALLBACK_URL =
    "https://latest.currency-api.pages.dev/v1/currencies/usd.json"

/**
 * Реальный источник курсов: fawazahmed0/exchange-api. Пробует [urls] по
 * порядку; если упали все — бросает исключение последней ошибки. Получение
 * текста вынесено в [get], чтобы провайдер проверялся без сети.
 */
class FawazRatesProvider(
    private val urls: List<String> =
        listOf(FAWAZ_PRIMARY_URL, FAWAZ_FALLBACK_URL),
    private val get: suspend (String) -> String,
) : RatesProvider {
    init {
        require(urls.isNotEmpty()) { "Нужен хотя бы один URL" }
    }

    override suspend fun fetch(): RatesSnapshot {
        var last: Exception? = null
        for (url in urls) {
            try {
                return parseRates(get(url))
            } catch (
                e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last!!
    }
}
