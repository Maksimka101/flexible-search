package com.zemlianikin.flexiblesearch.plugins.converter.rates

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024

/**
 * GET через общий OkHttp хоста. HTTP != 200 и ответ больше 4 МБ — ошибка.
 */
suspend fun OkHttpClient.httpGet(url: String): String =
    newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
        )
        .executeAsync()
        .use { response ->
            if (response.code != 200)
                throw IOException("HTTP ${response.code} для $url")
            val source = response.body.source()
            if (source.request(MAX_RESPONSE_BYTES + 1))
                throw IOException(
                    "Ответ больше $MAX_RESPONSE_BYTES байт: $url"
                )
            source.buffer.readUtf8()
        }
