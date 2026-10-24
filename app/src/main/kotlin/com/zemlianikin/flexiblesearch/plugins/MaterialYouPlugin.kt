package com.zemlianikin.flexiblesearch.plugins

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.search.SearchTheme

/**
 * Динамические цвета Android 12+, как у системы. Сменились обои — система
 * пересоздаёт окно, цвета новые.
 */
class MaterialYouPlugin : Plugin {
    override val id = "material-you"
    override val name = "Material You"
    override val description = "Colors from the wallpaper, Android 12+"

    override fun install(scope: PluginScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        scope.style(SearchTheme) {
            val context = LocalContext.current
            it.copy(
                colorScheme =
                    if (isSystemInDarkTheme()) {
                        dynamicDarkColorScheme(context)
                    } else {
                        dynamicLightColorScheme(context)
                    }
            )
        }
    }
}
