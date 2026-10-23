package com.zemlianikin.flexiblesearch.core

import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Превью по ключу результата; переживает смену запроса. */
private val thumbnails = LruCache<String, ImageBitmap>(200)

/** Сколько превью сейчас грузится: их ждёт первый кадр окна. */
internal class ThumbnailLoads {
    var pending by mutableIntStateOf(0)
}

internal val LocalThumbnailLoads =
    staticCompositionLocalOf<ThumbnailLoads?> { null }

/**
 * Превью строки: из кэша сразу, иначе грузится в фоне, пока строка на
 * экране. Ушла с экрана до загрузки — загрузка отменяется.
 */
@Composable
internal fun rememberThumbnail(
    key: String,
    loader: ThumbnailLoader?,
): ImageBitmap? {
    // Состояние по ключу: LazyColumn переиспользует слот строки, и без
    // ключа строка показала бы превью предыдущей.
    val image = remember(key) { mutableStateOf(thumbnails.get(key)) }
    val loads = LocalThumbnailLoads.current
    LaunchedEffect(key) {
        if (image.value != null || loader == null) return@LaunchedEffect
        loads?.let { it.pending++ }
        image.value =
            try {
                withContext(Dispatchers.IO) { loader() }
                    ?.also { thumbnails.put(key, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Thumbnails", "Can't load $key", e)
                null
            } finally {
                loads?.let { it.pending-- }
            }
    }
    return image.value
}
