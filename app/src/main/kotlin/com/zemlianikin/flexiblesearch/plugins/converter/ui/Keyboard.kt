package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.plugins.converter.data.KeyValueStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val NUMPAD_PREF = "numpad"

/**
 * Режим ввода и место под клавиатуру в текущем кадре. [numpad] — свои
 * кнопки вместо системной клавиатуры, [toggle] меняет режим, последний
 * запоминается. [reserve] — пустое место под выезжающую клавиатуру,
 * [numpadHeight] — высота блока numpad (`null`, пока высоту системной
 * клавиатуры не видели).
 */
class KeyboardLayout(
    val numpad: Boolean,
    val reserve: Dp,
    val numpadHeight: Dp?,
    val toggle: () -> Unit,
)

/**
 * Экран не прыгает при смене режима: в numpad вместо клавиатуры блок её
 * высоты, а пока клавиатура выезжает обратно, место под неё уже
 * зарезервировано. Окно поиска само отступает от клавиатуры (`imePadding`),
 * поэтому резерв — только то, до чего она ещё не доросла.
 */
@OptIn(FlowPreview::class)
@Composable
fun keyboardLayout(prefs: KeyValueStore): KeyboardLayout {
    var numpad by remember { mutableStateOf(prefs[NUMPAD_PREF] != "false") }
    val keyboardHeight = rememberKeyboardHeight(prefs)
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    var keyboardComing by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardComing) {
        if (keyboardComing) {
            // Снимаем резерв, когда IME дорос до запомненной высоты
            // (отступ тогда не меняется) или встал на месте (высота
            // изменилась). Раньше нельзя: отступ просел бы, а потом снова
            // вырос.
            val full = keyboardHeight.px - 2
            withTimeoutOrNull(1500) {
                snapshotFlow { ime.getBottom(density) }
                    .debounce { if (it >= full) 0L else 200L }
                    .first { it > 0 }
            }
            keyboardComing = false
        }
    }
    val bar = WindowInsets.navigationBars.getBottom(density)
    val reserve =
        if (keyboardComing) {
            val bottom = maxOf(bar, ime.getBottom(density))
            with(density) {
                (keyboardHeight.px - bottom).coerceAtLeast(0).toDp()
            }
        } else {
            0.dp
        }
    val numpadHeight =
        if (keyboardHeight.px == 0) null
        else
            with(density) {
                (keyboardHeight.px - bar).toDp().coerceAtLeast(240.dp)
            }
    return KeyboardLayout(
        numpad,
        reserve,
        numpadHeight,
        toggle = {
            numpad = !numpad
            keyboardComing = !numpad
            prefs[NUMPAD_PREF] = numpad.toString()
        },
    )
}

/**
 * Последняя высота системной клавиатуры в px от нижнего края экрана (вместе
 * с навбаром), [px] = 0 — ещё не видели. Numpad занимает ровно это место и
 * режимы переключаются без сдвига остального экрана.
 */
@Stable
class KeyboardHeight
internal constructor(
    private val prefs: KeyValueStore,
    private val key: String,
) {
    var px by mutableIntStateOf(prefs[key]?.toIntOrNull() ?: 0)
        private set

    internal fun remember(value: Int) {
        if (value <= 0 || value == px) return
        px = value
        prefs[key] = value.toString()
    }
}

/**
 * Следит за IME и хранит установившуюся высоту (без промежуточных кадров
 * анимации). Значение своё для каждого размера окна: у сложенного и
 * раскрытого экрана, портрета и альбома клавиатура разная. Клавиатура
 * общего поиска тоже считается: numpad сразу встаёт на её место.
 */
@OptIn(FlowPreview::class)
@Composable
private fun rememberKeyboardHeight(prefs: KeyValueStore): KeyboardHeight {
    val config = LocalConfiguration.current
    val key = "keyboard_${config.screenWidthDp}x${config.screenHeightDp}"
    val height = remember(key) { KeyboardHeight(prefs, key) }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(height) {
        snapshotFlow { ime.getBottom(density) }
            .debounce(300)
            .collect { height.remember(it) }
    }
    return height
}
