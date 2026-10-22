package com.zemlianikin.flexiblesearch.core

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient

/**
 * Общее для всех плагинов: один пул соединений и HTTP-кэш, один [Json].
 * Создаётся при первом обращении, а не при старте.
 */
internal class HostServices(context: Context) {
    var providerTimeLimitMs = SearchEngine.DEFAULT_PROVIDER_TIME_LIMIT_MS

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_BYTES))
            .build()
    }

    val json = Json {
        // Ответы чужих API растут: новое поле не должно ронять плагин.
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private companion object {
        const val HTTP_CACHE_BYTES = 10L * 1024 * 1024
    }
}
