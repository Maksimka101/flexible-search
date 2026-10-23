package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
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
 * Режим ввода: [numpad] — свои кнопки вместо системной клавиатуры, [toggle]
 * меняет режим, последний запоминается. [space] — сколько занимает
 * системная клавиатура над навбаром: столько же занимает numpad, и
 * остальной экран при смене не двигается.
 */
class KeyboardLayout(
    val numpad: Boolean,
    val space: Dp,
    val toggle: () -> Unit,
)

@Composable
fun keyboardLayout(prefs: KeyValueStore): KeyboardLayout {
    var numpad by remember { mutableStateOf(prefs[NUMPAD_PREF] != "false") }
    val keyboardHeight = rememberKeyboardHeight(prefs)
    val density = LocalDensity.current
    val bar = WindowInsets.navigationBars.getBottom(density)
    // Клавиатуру ещё не видели — обычная для телефона высота.
    val space =
        if (keyboardHeight.px == 0) 330.dp
        else with(density) { (keyboardHeight.px - bar).toDp() }
    return KeyboardLayout(
        numpad,
        space,
        toggle = {
            numpad = !numpad
            prefs[NUMPAD_PREF] = numpad.toString()
        },
    )
}

/**
 * Отступ экрана от системной клавиатуры: в конвертере ([active]) и сразу
 * после него, иначе [default]. Numpad сам стоит на месте клавиатуры, и с
 * ним отступа нет сразу, не дожидаясь, пока клавиатура уедет: иначе numpad
 * на кадр встаёт над ней. Без numpad — конечная высота клавиатуры, без
 * анимации. Когда клавиатура снова нужна (выход, смена режима), система ещё
 * ~150 мс не знает её высоту: пока — запомненная, иначе экран падает на
 * место numpad и поднимается обратно.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardLayout.insets(
    active: Boolean,
    default: WindowInsets,
): WindowInsets {
    val shown = active && numpad
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget
    // Numpad был, а клавиатура ещё не встала.
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown) {
            settling = true
        } else if (settling) {
            // Клавиатура может и не появиться: физическая, смахнули.
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                snapshotFlow {
                    val bottom = ime.getBottom(density)
                    bottom > 0 && bottom == target.getBottom(density)
                }
                    .first { it }
            }
            settling = false
        }
    }
    return when {
        shown -> WindowInsets(0)
        settling && target.getBottom(density) == 0 ->
            WindowInsets(bottom = space).add(WindowInsets.navigationBars)
        active || settling -> target
        else -> default
    }
}

private const val SETTLE_TIMEOUT_MS = 1000L

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
