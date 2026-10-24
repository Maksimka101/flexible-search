package com.zemlianikin.flexiblesearch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.zemlianikin.flexiblesearch.core.PerfTrace
import com.zemlianikin.flexiblesearch.core.SearchLinks
import com.zemlianikin.flexiblesearch.core.SearchScreen
import com.zemlianikin.flexiblesearch.core.SearchWindow
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SearchActivity : ComponentActivity() {
    private val window =
        object : SearchWindow {
            override val context: Context
                get() = this@SearchActivity

            // Как Pixel Search: окно поверх экрана, не в многозадачности.
            // Уходит в фон живым: заново открывается без пересоздания,
            // а прозрачное окно до первого кадра ничего не показывает.
            override fun close() {
                moveTaskToBack(true)
            }
        }

    // SearchLinks: канал отдаёт каждую ссылку один раз.
    private val links = Channel<Uri>(Channel.UNLIMITED)
    private val linkFlow = links.receiveAsFlow()

    // Сброс строки, пока окно в фоне.
    private var reset: Job? = null

    private val background by lazy {
        WallpaperBackground(getWindow(), windowManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PerfTrace.mark("activity created")
        super.onCreate(savedInstanceState)
        // После пересоздания (складывание) intent тот же, ссылка уже
        // применена.
        if (savedInstanceState == null) intent.data?.let(links::trySend)
        enableEdgeToEdge()
        val app = application as SearchApp
        // Первое значение приходит сразу, до первого кадра.
        lifecycleScope.launch {
            app.wallpaper.bitmap.collect(background::show)
        }
        setContent {
            SearchScreen(app.plugins, window, linkFlow) {
                WallpaperAccess()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        reset?.cancel()
        (application as SearchApp).wallpaper.refresh()
    }

    // Вернулись быстро — строка та же, надолго ушли — пустая, как в
    // Pixel Search. Сбрасывается в фоне, чтобы не тормозить открытие.
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        reset = lifecycleScope.launch {
            delay(RESET_DELAY)
            links.trySend(SearchLinks.search(""))
        }
    }

    override fun onDestroy() {
        background.dispose()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(links::trySend)
    }

    private companion object {
        val RESET_DELAY = 15.seconds
    }
}
