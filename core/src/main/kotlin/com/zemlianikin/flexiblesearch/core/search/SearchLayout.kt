package com.zemlianikin.flexiblesearch.core.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleStartEffect
import com.zemlianikin.flexiblesearch.core.component.Component
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

// --- Экран: только расставляет детей, как Scaffold. Строку вниз —
// переопределить его. ---

class SearchLayoutProps(
    val searchBar: @Composable () -> Unit,
    val loading: @Composable () -> Unit,
    /** На пустой строке — и виджеты, в той же прокрутке. */
    val results: @Composable () -> Unit,
    /** Ряд кнопок у клавиатуры, виден всегда. */
    val quickActions: @Composable () -> Unit,
    val onDismiss: () -> Unit,
)

data class SearchLayoutStyle(
    /**
     * Поверх обоев. По умолчанию прозрачный: размытие и затемнение обоев —
     * у окна, они видны с первого кадра.
     */
    val background: Color,
    val padding: PaddingValues,
    val spacing: Dp,
    /**
     * Отступ от клавиатуры. По умолчанию [rememberSteadyKeyboardInsets]:
     * при открытии окна экран сразу на месте, потом едет вместе с
     * клавиатурой. `WindowInsets.ime` — едет всегда,
     * `WindowInsets.imeAnimationTarget` — сразу конечный, без анимации.
     */
    val keyboardInsets: WindowInsets,
)

val SearchLayout =
    Component<SearchLayoutProps, SearchLayoutStyle>(
        name = "SearchLayout",
        defaultStyle = {
            SearchLayoutStyle(
                Color.Transparent,
                PaddingValues(12.dp),
                12.dp,
                rememberSteadyKeyboardInsets(),
            )
        },
    ) { props, style ->
        Box(
            Modifier.fillMaxSize()
                .background(style.background)
                // Нажатие мимо результатов закрывает поиск, как в Pixel
                // Search.
                .clickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = props.onDismiss,
                )
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(
                        WindowInsets.displayCutout
                    )
                )
                .windowInsetsPadding(style.keyboardInsets)
                .padding(style.padding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(style.spacing)
            ) {
                props.searchBar()
                // Под строкой: появляясь, он сжимает область результатов
                // сверху, а прижатые к низу результаты не двигаются.
                props.loading()
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    props.results()
                }
                props.quickActions()
            }
        }
    }

/** Дольше клавиатуру при открытии не ждём: её может и не быть. */
private const val KEYBOARD_TIMEOUT_MS = 2000L

/**
 * Отступ от клавиатуры, который не едет при открытии окна: пока она
 * выезжает, снизу сразу её прошлая высота (своя для каждого размера
 * экрана), а когда выехала — как [WindowInsets.ime].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberSteadyKeyboardInsets(): WindowInsets {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val prefs = remember {
        context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
    }
    val key = "${config.screenWidthDp}x${config.screenHeightDp}"
    val saved = remember(key) { mutableIntStateOf(prefs.getInt(key, 0)) }
    val opening = remember { mutableStateOf(true) }
    LifecycleStartEffect(Unit) {
        opening.value = true
        onStopOrDispose {}
    }
    val ime = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget
    // Выехала целиком — запомнить высоту, а экран дальше едет с ней.
    LaunchedEffect(key) {
        snapshotFlow { ime.getBottom(density) to target.getBottom(density) }
            .filter { (now, end) -> now > 0 && now == end }
            .collect { (now, _) ->
                if (now != saved.intValue) {
                    saved.intValue = now
                    prefs.edit { putInt(key, now) }
                }
                opening.value = false
            }
    }
    LaunchedEffect(opening.value) {
        if (opening.value) {
            delay(KEYBOARD_TIMEOUT_MS)
            opening.value = false
        }
    }
    return remember(ime, saved) {
        object : WindowInsets {
            override fun getLeft(
                density: Density,
                layoutDirection: LayoutDirection,
            ) = 0

            override fun getTop(density: Density) = 0

            override fun getRight(
                density: Density,
                layoutDirection: LayoutDirection,
            ) = 0

            override fun getBottom(density: Density): Int {
                val now = ime.getBottom(density)
                return if (opening.value) maxOf(now, saved.intValue)
                else now
            }
        }
    }
}
