package com.zemlianikin.flexiblesearch.poc.components

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zemlianikin.flexiblesearch.poc.components.core.SearchScreen
import com.zemlianikin.flexiblesearch.poc.components.core.SearchWindow

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val host = (application as SearchApp).host
        setContent { SearchScreen(host, window) }
    }
}
