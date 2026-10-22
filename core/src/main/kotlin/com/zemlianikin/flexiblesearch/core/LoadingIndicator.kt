package com.zemlianikin.flexiblesearch.core

import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// --- Загрузка ---

class LoadingIndicatorProps(val loading: Boolean)

data class LoadingIndicatorStyle(
    val color: Color,
    val trackColor: Color,
    /** Быстрые ответы не показывают индикатор вовсе. */
    val showDelayMs: Long,
    /** Показанный индикатор не мелькает, даже если загрузка кончилась. */
    val minVisibleMs: Long,
)

val LoadingIndicator =
    Component<LoadingIndicatorProps, LoadingIndicatorStyle>(
        name = "LoadingIndicator",
        defaultStyle = {
            LoadingIndicatorStyle(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                showDelayMs = 300,
                minVisibleMs = 500,
            )
        },
    ) { props, style ->
        val visible =
            delayedLoading(
                props.loading,
                style.showDelayMs,
                style.minVisibleMs,
            )
        if (visible) {
            LinearProgressIndicator(
                color = style.color,
                trackColor = style.trackColor,
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    }

/**
 * Видна ли загрузка: появляется, если [loading] держится дольше
 * [showDelayMs], и не пропадает раньше [minVisibleMs].
 */
@Composable
fun delayedLoading(
    loading: Boolean,
    showDelayMs: Long,
    minVisibleMs: Long,
): Boolean {
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading && !visible) {
            delay(showDelayMs)
            visible = true
            shownAt = SystemClock.uptimeMillis()
        } else if (!loading && visible) {
            delay(shownAt + minVisibleMs - SystemClock.uptimeMillis())
            visible = false
        }
    }
    return visible
}
