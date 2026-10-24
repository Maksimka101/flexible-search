package com.zemlianikin.flexiblesearch.plugins.converter

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zemlianikin.flexiblesearch.core.Action
import com.zemlianikin.flexiblesearch.core.ActionIcon
import com.zemlianikin.flexiblesearch.core.ActionScope
import com.zemlianikin.flexiblesearch.core.LocalActionScope
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.QuickActions
import com.zemlianikin.flexiblesearch.core.QuickActionsProps
import com.zemlianikin.flexiblesearch.core.SearchBar
import com.zemlianikin.flexiblesearch.core.SearchLayout
import com.zemlianikin.flexiblesearch.core.SearchLayoutProps
import com.zemlianikin.flexiblesearch.core.SearchQuery
import com.zemlianikin.flexiblesearch.core.materialIcon
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Value
import com.zemlianikin.flexiblesearch.plugins.converter.calc.defaultCalculator
import com.zemlianikin.flexiblesearch.plugins.converter.core.DecayingFrecency
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable
import com.zemlianikin.flexiblesearch.plugins.converter.core.today
import com.zemlianikin.flexiblesearch.plugins.converter.data.KeyValueUsageStore
import com.zemlianikin.flexiblesearch.plugins.converter.data.SharedPrefsStore
import com.zemlianikin.flexiblesearch.plugins.converter.data.localeSeed
import com.zemlianikin.flexiblesearch.plugins.converter.rates.CachedRatesRepository
import com.zemlianikin.flexiblesearch.plugins.converter.rates.FawazRatesProvider
import com.zemlianikin.flexiblesearch.plugins.converter.rates.FileRatesCache
import com.zemlianikin.flexiblesearch.plugins.converter.rates.httpGet
import com.zemlianikin.flexiblesearch.plugins.converter.ui.CalculatorState
import com.zemlianikin.flexiblesearch.plugins.converter.ui.ConverterKeys
import com.zemlianikin.flexiblesearch.plugins.converter.ui.ConverterResults
import com.zemlianikin.flexiblesearch.plugins.converter.ui.KeyboardLayout
import com.zemlianikin.flexiblesearch.plugins.converter.ui.SettingsContent
import com.zemlianikin.flexiblesearch.plugins.converter.ui.ValueFormatter
import com.zemlianikin.flexiblesearch.plugins.converter.ui.WithoutSystemKeyboard
import com.zemlianikin.flexiblesearch.plugins.converter.ui.conversions
import com.zemlianikin.flexiblesearch.plugins.converter.ui.insets
import com.zemlianikin.flexiblesearch.plugins.converter.ui.keyboardLayout
import com.zemlianikin.flexiblesearch.plugins.converter.ui.rememberCalculatorState
import com.zemlianikin.flexiblesearch.plugins.converter.ui.underlineError
import java.io.File
import kotlin.time.Clock

/**
 * Калькулятор и конвертер валют из currency-converter: `2+2*2`, `10 usd -
 * 10% to eur`, `5k руб в $`. Без префикса — карточка в общем поиске.
 * Префикс `ca` или `=` (меняются в настройках), кнопка в ряду и на главном
 * экране открывают экран конвертера: свой numpad вместо клавиатуры, чипы
 * валют, сумма во всех частых валютах. Курсы — fawazahmed0/exchange-api,
 * раз в сутки, с офлайн-кэшем.
 */
class ConverterPlugin : Plugin {
    override val id = "converter"
    override val name = "Calculator & converter"
    override val description =
        "2+2 and 10 usd to eur as you type, ca for the numpad"

    override fun install(scope: PluginScope) {
        val repository =
            CachedRatesRepository(
                FawazRatesProvider { scope.http.httpGet(it) },
                FileRatesCache(
                    File(scope.context.filesDir, "converter/rates.txt")
                ),
            )
        val calculator = defaultCalculator()
        // Один файл настроек плагина: frecency и режим клавиатуры в нём
        // под своими префиксами.
        val usage =
            KeyValueUsageStore(SharedPrefsStore(scope.prefs, "usage."))
        val uiPrefs = SharedPrefsStore(scope.prefs, "ui.")
        val settings =
            ConverterSettings(
                SharedPrefsStore(scope.prefs, "settings."),
                defaultPrefixes = listOf("ca", "="),
            )
        val icon = ActionIcon.Vector(CalculatorIcon)
        val trigger =
            scope.triggers.add(
                "Calculator",
                *settings.prefixes.value.toTypedArray(),
                placeholder = "10 usd - 10% to eur",
                icon = icon,
            )
        scope.buttons.quickAction(
            "Calculator",
            icon,
            id = QUICK_ACTION,
            isSelected = { it.trigger == trigger },
        ) {
            // Повторное нажатие убирает чип, набранное остаётся.
            if (query.trigger == trigger) setQuery(SearchQuery(query.text))
            else setQuery(SearchQuery(query.text, trigger))
        }
        val openCalculator: Action = { setQuery(SearchQuery("", trigger)) }
        scope.launcher.widgetButton(
            "Calculator",
            icon,
            visible = settings.homeScreenButton,
            action = openCalculator,
        )
        val shortcut =
            scope.launcher.shortcut(
                "Calculator",
                icon,
                action = openCalculator,
            )
        val quickActionId = "$id/$QUICK_ACTION"
        scope.override(QuickActions) { props, style, original ->
            val visible by settings.quickAction.collectAsState()
            if (visible) original(props, style)
            else
                original(
                    QuickActionsProps(
                        props.actions.filter { it.id != quickActionId },
                        props.query,
                        props.onClick,
                    ),
                    style,
                )
        }
        val settingsScreen =
            scope.screens.add(name) {
                SettingsContent(
                    settings,
                    onPin =
                        if (scope.launcher.canPin) {
                            { scope.launcher.pin(shortcut) }
                        } else null,
                ) {
                    settings.setPrefixes(it)
                    scope.triggers.setPrefixes(trigger, it)
                }
            }
        scope.screens.settings { open(settingsScreen) }

        // Состояние экрана живёт в окне, а не в строке поиска: его читают
        // и поле ввода, и numpad, и результаты.
        scope.override(SearchLayout) { props, style, original ->
            val actions = LocalActionScope.current
            val query = actions.query
            val active = query.trigger == trigger
            val ratesState by repository.state.collectAsState()
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    repository.refreshIfStale()
                }
            }
            val snapshot = ratesState.cached?.snapshot
            // Курсов ещё нет — пустая таблица: калькулятор даст NoRate, а
            // экран по ratesState покажет загрузку.
            val rates =
                remember(snapshot) {
                    snapshot?.toTable() ?: RateTable(today(), emptyMap())
                }
            // Список валют frecency — из снимка; пока курсов нет, он пуст,
            // а когда снимок пришёл, frecency пересоздаётся.
            val currencies =
                remember(snapshot) {
                    snapshot?.currencies.orEmpty().toSet()
                }
            val frecency =
                remember(currencies) {
                    DecayingFrecency(
                        usage,
                        currencies.toList(),
                        localeSeed(),
                    )
                }
            val state = rememberCalculatorState(calculator, rates, frecency)
            // Всегда, а не только с префиксом: высота клавиатуры общего
            // поиска нужна numpad.
            val keyboard = keyboardLayout(uiPrefs)
            val format = remember { ValueFormatter() }
            state.onEdit = { actions.setQuery(SearchQuery(it, trigger)) }
            if (active) state.sync(query.text)
            val screen = ConverterScreen(state, keyboard)
            val keys: @Composable () -> Unit = {
                Column {
                    props.quickActions()
                    ConverterKeys(
                        state,
                        keyboard,
                        format,
                        visible = active,
                        spacing = style.spacing,
                    ) {
                        // ⌫ на пустом вводе убирает префикс, как на
                        // клавиатуре.
                        if (state.input.text.isEmpty()) {
                            actions.setQuery("")
                        } else {
                            state.backspace()
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalConverter provides screen) {
                original(
                    SearchLayoutProps(
                        searchBar = props.searchBar,
                        loading = if (active) ({}) else props.loading,
                        results =
                            if (active) {
                                {
                                    ConverterResults(
                                        state,
                                        ratesState,
                                        format,
                                    )
                                }
                            } else {
                                props.results
                            },
                        quickActions = keys,
                        onDismiss = props.onDismiss,
                    ),
                    style.copy(
                        keyboardInsets =
                            keyboard.insets(active, style.keyboardInsets)
                    ),
                )
            }
        }

        scope.override(SearchBar) { props, style, original ->
            val screen = LocalConverter.current
            val focus = props.focusRequester
            if (props.trigger != trigger || screen == null) {
                original(props, style)
                // Вернулись из numpad: поле новое, фокус и клавиатуру
                // возвращаем.
                LaunchedEffect(Unit) {
                    runCatching { focus.requestFocus() }
                }
                return@override
            }
            val state = screen.state
            val numpad = screen.keyboard.numpad
            val error = MaterialTheme.colorScheme.error
            // Строка та же, но текст с курсором — у состояния конвертера:
            // numpad набирает в позицию курсора. Сессия ввода живёт, пока
            // поле в композиции, поэтому при смене режима поле
            // пересоздаётся.
            key(numpad) {
                WithoutSystemKeyboard(numpad) {
                    original(
                        props.copy(
                            value = state.input,
                            onValueChange = state::edit,
                            visualTransformation =
                                underlineError(state.result, error),
                        ),
                        style,
                    )
                }
            }
            val systemKeyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(numpad) {
                // Поле пересоздаётся при смене режима.
                runCatching { focus.requestFocus() }
                if (numpad) systemKeyboard?.hide()
                else systemKeyboard?.show()
            }
        }

        scope.results.add { query ->
            val snapshot = repository.state.value.cached?.snapshot
            val rates =
                snapshot?.toTable() ?: RateTable(today(), emptyMap())
            val ok =
                calculator.calculate(query, rates) as? Calculation.Ok
                    ?: return@add
            if (!worthShowing(query, ok)) return@add
            val format = ValueFormatter()
            val value = format.value(ok.value)
            val money = ok.value as? Value.Money
            // Под суммой — она же в самых частых валютах.
            val conversions =
                if (money == null || snapshot == null) ""
                else {
                    val ranking =
                        DecayingFrecency(
                                usage,
                                snapshot.currencies,
                                localeSeed(),
                            )
                            .ranking(Clock.System.now())
                    conversions(money, ranking, rates)
                        .take(INLINE_CONVERSIONS)
                        .joinToString(" · ") { format.value(it) }
                }
            // Постоянный id: при наборе карточка не переезжает в конец.
            card(
                "= $value",
                conversions.ifEmpty { "${query.trim()} · tap to copy" },
                id = "result",
            ) {
                copy(value)
            }
        }
    }

    private fun ActionScope.copy(text: String) {
        context
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Result", text))
        // С Android 13 система сама показывает, что скопировано.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private companion object {
        const val INLINE_CONVERSIONS = 3
        const val QUICK_ACTION = "calculator"
    }
}

/** Что окно конвертера отдаёт строке поиска. */
private class ConverterScreen(
    val state: CalculatorState,
    val keyboard: KeyboardLayout,
)

private val LocalConverter = compositionLocalOf<ConverterScreen?> { null }

/**
 * Карточка в общем поиске — только если это похоже на пример, а не на
 * поиск: `42`, `-5`, `5k` и голая валюта (`usd`) — нет. Число — если есть
 * операция, сумма — если есть число или вторая валюта (`eur to usd`).
 */
internal fun worthShowing(query: String, ok: Calculation.Ok): Boolean =
    when (ok.value) {
        is Value.Number -> {
            val body = query.trim().trimStart('-', '−', '+')
            body.any { it in "+-−–*×·/÷%" } ||
                WORD_OPERATION.containsMatchIn(body)
        }
        is Value.Money ->
            query.any { it.isDigit() } || ok.currencies.size > 1
        is Value.Ratio -> true
    }

private val WORD_OPERATION = Regex("""(\d|\s)(x|х|of|от)(\s|\d|$)""")

// Material Symbols «calculate».
private val CalculatorIcon: ImageVector =
    materialIcon(
        "Calculator",
        "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14" +
            "c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z" +
            "M13.03,7.06L14.09,6l1.41,1.41L16.91,6l1.06,1.06" +
            "l-1.41,1.41 1.41,1.41 -1.06,1.06 -1.41,-1.4" +
            "l-1.41,1.41 -1.06,-1.06 1.41,-1.41L13.03,7.06z" +
            "M6.25,7.72h5v1.5h-5V7.72z" +
            "M11.5,16h-2v2H8v-2H6v-1.5h2v-2h1.5v2h2V16z" +
            "M18,17.25h-5v-1.5h5V17.25z" +
            "M18,14.75h-5v-1.5h5V14.75z",
    )
