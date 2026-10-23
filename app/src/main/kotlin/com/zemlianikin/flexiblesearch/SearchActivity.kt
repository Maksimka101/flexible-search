package com.zemlianikin.flexiblesearch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zemlianikin.flexiblesearch.core.SearchScreen
import com.zemlianikin.flexiblesearch.core.SearchWindow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class SearchActivity : ComponentActivity() {
    private val window =
        object : SearchWindow {
            override val context: Context
                get() = this@SearchActivity

            // Как обычное приложение: уходит в фон и остаётся в
            // многозадачности со своей строкой.
            override fun close() {
                moveTaskToBack(true)
            }
        }

    // SearchLinks: канал отдаёт каждую ссылку один раз.
    private val links = Channel<Uri>(Channel.UNLIMITED)
    private val linkFlow = links.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // После пересоздания (складывание) intent тот же, ссылка уже
        // применена.
        if (savedInstanceState == null) intent.data?.let(links::trySend)
        enableEdgeToEdge()
        val app = application as SearchApp
        setContent {
            val disabled by app.pluginSettings.disabled.collectAsState()
            SearchScreen(app.host(disabled), window, linkFlow)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(links::trySend)
    }
}
