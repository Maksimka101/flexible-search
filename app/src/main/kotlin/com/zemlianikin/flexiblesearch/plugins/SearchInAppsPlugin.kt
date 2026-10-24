package com.zemlianikin.flexiblesearch.plugins

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import com.zemlianikin.flexiblesearch.core.ActionIcon
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.QuickAction
import com.zemlianikin.flexiblesearch.core.QuickActions
import com.zemlianikin.flexiblesearch.core.QuickActionsProps
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Ряд «искать в…»: строка уходит в веб-поиск, чаты, карты, магазины и
 * встроенный поиск приложений. Кнопка показывается, только если приложение
 * установлено, включено и принимает такой интент, и не скрыта в настройках
 * плагина. Enter без результатов — веб-поиск в браузере по умолчанию.
 */
class SearchInAppsPlugin : Plugin {
    override val id = "search-in-apps"
    override val name = "Search in apps"
    override val description =
        "Send the query to web search, AI chats, maps and other apps"

    private class Target(
        val label: String,
        val packageName: String,
        /**
         * Явная активити: если интент принимают несколько или фильтр без
         * категории DEFAULT.
         */
        val activity: String? = null,
        val intent: (query: String) -> Intent,
    )

    private fun view(uri: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    private fun webSearch(query: String) =
        Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)

    /** Поиск внутри приложения, его searchable-активити. */
    private fun search(query: String) =
        Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, query)

    /** Текст в поле ввода чата, как через «Поделиться». */
    private fun send(query: String) =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, query)

    /** Поиск Samsung Finder: своё действие, запрос в экстре `query`. */
    private fun finderSearch(action: String, query: String) =
        Intent(action).putExtra("query", query)

    private val targets =
        listOf(
            Target("Web", "com.google.android.googlequicksearchbox") {
                webSearch(it)
            },
            Target("Claude", "com.anthropic.claude") {
                view("https://claude.ai/new?q=${Uri.encode(it)}")
            },
            Target("ChatGPT", "com.openai.chatgpt") { send(it) },
            Target("Gemini", "com.google.android.apps.bard") { send(it) },
            Target("Perplexity", "ai.perplexity.app.android") { send(it) },
            Target("Maps", "com.google.android.apps.maps") {
                view("geo:0,0?q=${Uri.encode(it)}")
            },
            Target("Yandex Maps", "ru.yandex.yandexmaps") {
                view("yandexmaps://maps.yandex.ru/?text=${Uri.encode(it)}")
            },
            Target("Translate", "com.google.android.apps.translate") {
                Intent(Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, it)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            },
            Target("YouTube", "com.google.android.youtube") { search(it) },
            Target("Yandex Music", "ru.yandex.music") {
                view(
                    "https://music.yandex.ru/search?text=${Uri.encode(it)}"
                )
            },
            Target("Telegram", "org.telegram.messenger") {
                view("tg://search?query=${Uri.encode(it)}")
            },
            Target("Messages", "com.google.android.apps.messaging") {
                search(it)
            },
            Target(
                "Contacts",
                "com.samsung.android.app.contacts",
                "com.samsung.android.contacts.search.activity" +
                    ".ContactSearchActivity",
            ) {
                search(it)
            },
            Target("Calendar", "com.samsung.android.calendar") {
                search(it)
            },
            Target("Notes", "com.samsung.android.app.notes") { search(it) },
            Target("Reminder", "com.samsung.android.app.reminder") {
                search(it)
            },
            // Поиск, которым их открывает Samsung Finder.
            Target(
                "Gallery",
                "com.sec.android.gallery3d",
                "com.samsung.android.gallery.app.activity.external" +
                    ".GalleryExternalActivity",
            ) {
                finderSearch(
                    "com.android.gallery.action" +
                        ".SEARCH_FROM_GALAXY_FINDER_VIEW",
                    it,
                )
            },
            Target("My Files", "com.sec.android.app.myfiles") {
                finderSearch(
                    "com.sec.android.app.myfiles.FINDER_SEARCH_IN_APP",
                    it,
                )
            },
            Target(
                "Settings",
                "com.android.settings.intelligence",
                "com.android.settings.intelligence.search.SearchActivity",
            ) {
                search(it)
            },
            Target("Threads", "com.instagram.barcelona") {
                view("https://www.threads.com/search?q=${Uri.encode(it)}")
            },
            Target("Bluesky", "xyz.blueskyweb.app") {
                view("https://bsky.app/search?q=${Uri.encode(it)}")
            },
            Target("Steam", "com.valvesoftware.android.steam.community") {
                view(
                    "https://store.steampowered.com/search/" +
                        "?term=${Uri.encode(it)}"
                )
            },
            Target("Firefox", "org.mozilla.firefox") { webSearch(it) },
            Target("Chrome", "com.android.chrome") { webSearch(it) },
            Target("Samsung Internet", "com.sec.android.app.sbrowser") {
                webSearch(it)
            },
            Target("Play", "com.android.vending") {
                view("market://search?q=${Uri.encode(it)}")
            },
        )

    override fun install(scope: PluginScope) {
        val frecency = Frecency(scope.prefs)
        val hidden = HiddenTargets(scope.prefs)
        val handler = Handler(Looper.getMainLooper())
        val labels = mutableListOf<String>()
        // Подписи кнопок по убыванию frecency.
        val order = MutableStateFlow(emptyList<String>())
        val packageManager = scope.context.packageManager
        val iconSize =
            (28 * scope.context.resources.displayMetrics.density).toInt()
        val available = mutableSetOf<String>()
        for (target in targets) {
            // Активити ищется один раз при установке плагина, иконка —
            // в фоне при первом показе.
            val component = resolve(packageManager, target) ?: continue
            available += target.packageName
            val icon =
                ActionIcon.Lazy("$id/${target.packageName}") {
                    packageManager
                        .getApplicationIcon(target.packageName)
                        .toBitmap(iconSize, iconSize)
                        .asImageBitmap()
                }
            scope.buttons.quickAction(
                target.label,
                icon,
                id = target.packageName,
                onRemove = {
                    hidden.set(target.packageName, hidden = true)
                },
            ) {
                val intent =
                    target
                        .intent(query.text.trim())
                        .setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (start(context, intent)) {
                    close()
                    frecency.recordUsed(target.label)
                    // Ряд перестраивается, когда поиск уже скрылся за
                    // запущенным приложением, иначе кажется, что нажата
                    // соседняя кнопка.
                    handler.postDelayed(
                        { order.value = frecency.ranking(labels) },
                        REORDER_DELAY_MS,
                    )
                }
            }
            labels += target.label
        }
        order.value = frecency.ranking(labels)

        // С чипом строка — команда, а не запрос: Enter дальше.
        scope.results.onSubmit { results, next ->
            val text = query.text.trim()
            when {
                results.items.isNotEmpty() ||
                    query.trigger != null ||
                    text.isEmpty() -> next()
                start(context, browserSearch(packageManager, text)) ->
                    close()
            }
        }

        val prefix = "$id/"
        scope.override(QuickActions) { props, style, original ->
            val ranking by order.collectAsState()
            val hiddenPackages by hidden.packages.collectAsState()
            val rank =
                ranking.withIndex().associate { it.value to it.index }
            val labels = targets.associate { it.packageName to it.label }
            fun isHidden(action: QuickAction) =
                action.id.removePrefix(prefix) in hiddenPackages
            val own =
                props.actions
                    .filter { it.id.startsWith(prefix) && !isHidden(it) }
                    .sortedBy { rank[labels[it.id.removePrefix(prefix)]] }
                    .iterator()
            // Свои кнопки занимают свои же места, чужие стоят как стояли.
            val actions =
                props.actions.mapNotNull {
                    when {
                        !it.id.startsWith(prefix) -> it
                        isHidden(it) -> null
                        else -> own.next()
                    }
                }
            original(
                QuickActionsProps(actions, props.query, props.onClick),
                style,
            )
        }

        val settings =
            scope.screens.add(name) {
                val hiddenPackages by hidden.packages.collectAsState()
                // Сначала — какие были при установке плагина, потом —
                // перечитанные: приложение могли поставить или удалить.
                val installed by
                    produceState<Set<String>>(available) {
                        value =
                            withContext(Dispatchers.IO) {
                                targets
                                    .filter {
                                        resolve(packageManager, it) != null
                                    }
                                    .map { it.packageName }
                                    .toSet()
                            }
                    }
                LazyColumn {
                    items(
                        targets.sortedBy { it.packageName !in installed },
                        key = { it.packageName },
                    ) { target ->
                        TargetRow(
                            target,
                            installed = target.packageName in installed,
                            visible = target.packageName !in hiddenPackages,
                            packageManager,
                        ) {
                            hidden.set(target.packageName, hidden = !it)
                        }
                    }
                }
            }
        scope.screens.settings { open(settings) }
    }

    /**
     * Цель в настройках. Неустановленная — тусклая и без значка, но скрыть
     * можно и её: не появится, когда приложение поставят.
     */
    @Composable
    private fun TargetRow(
        target: Target,
        installed: Boolean,
        visible: Boolean,
        packageManager: PackageManager,
        onVisibleChange: (Boolean) -> Unit,
    ) {
        val iconSize = 40.dp
        val size = with(LocalDensity.current) { iconSize.roundToPx() }
        val icon by
            produceState<ImageBitmap?>(
                null,
                target.packageName,
                installed,
            ) {
                if (!installed) return@produceState
                value =
                    withContext(Dispatchers.IO) {
                        try {
                            packageManager
                                .getApplicationIcon(target.packageName)
                                .toBitmap(size, size)
                                .asImageBitmap()
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
            }
        val dimmed =
            MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        ListItem(
            leadingContent = {
                val image = icon
                if (image != null) {
                    Image(image, null, Modifier.size(iconSize))
                } else {
                    Spacer(Modifier.size(iconSize))
                }
            },
            headlineContent = { Text(target.label) },
            supportingContent =
                if (installed) null else ({ Text("Not installed") }),
            trailingContent = {
                // Нажатие обрабатывает вся строка. LazyColumn отдаёт
                // ушедшую строку другой цели, а Switch анимирует смену
                // checked в своём узле — key создаёт узел заново.
                key(target.packageName) {
                    Switch(checked = visible, onCheckedChange = null)
                }
            },
            colors =
                if (installed) ListItemDefaults.colors()
                else
                    ListItemDefaults.colors(
                        headlineColor = dimmed,
                        supportingColor = dimmed,
                    ),
            modifier =
                Modifier.toggleable(
                    value = visible,
                    role = Role.Switch,
                    onValueChange = onVisibleChange,
                ),
        )
    }

    /**
     * Активити, которой уйдёт запрос; `null`, если приложения нет, оно
     * выключено или интент не принимает.
     */
    private fun resolve(
        packageManager: PackageManager,
        target: Target,
    ): ComponentName? {
        val intent = target.intent("").setPackage(target.packageName)
        val info =
            if (target.activity != null) {
                intent.setClassName(target.packageName, target.activity)
                packageManager.resolveActivity(intent, 0)
            } else {
                packageManager
                    .queryIntentActivities(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY,
                    )
                    .firstOrNull()
            }
        val activity = info?.activityInfo ?: return null
        if (!activity.exported) return null
        return ComponentName(activity.packageName, activity.name)
    }

    /**
     * Поиск в браузере по умолчанию его поисковиком; не принимает
     * ACTION_WEB_SEARCH или браузер не выбран — страница Google.
     */
    private fun browserSearch(
        packageManager: PackageManager,
        query: String,
    ): Intent {
        val browser =
            packageManager
                .resolveActivity(
                    view("http://"),
                    PackageManager.MATCH_DEFAULT_ONLY,
                )
                ?.activityInfo
                ?.packageName
        val search = webSearch(query).setPackage(browser)
        val intent =
            if (
                browser != null &&
                    search.resolveActivity(packageManager) != null
            )
                search
            else
                view("https://www.google.com/search?q=${Uri.encode(query)}")
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun start(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("SearchInAppsPlugin", "Can't start $intent", e)
            false
        }

    private companion object {
        const val REORDER_DELAY_MS = 1000L

        /** Как у выключенного текста в Material 3. */
        const val DISABLED_ALPHA = 0.38f
    }
}

/** Скрытые в настройках цели, по пакету. */
private class HiddenTargets(private val prefs: SharedPreferences) {
    private val state =
        MutableStateFlow(prefs.getStringSet(KEY, null).orEmpty().toSet())

    val packages: StateFlow<Set<String>> = state

    fun set(packageName: String, hidden: Boolean) {
        val packages =
            if (hidden) state.value + packageName
            else state.value - packageName
        prefs.edit { putStringSet(KEY, packages) }
        state.value = packages
    }

    private companion object {
        const val KEY = "hidden"
    }
}

/**
 * Frecency: `score·2^(-Δt/halfLife) + 1` на каждое нажатие, распад ленивый,
 * как у валют в конвертере. В [prefs] — `score:millis` по подписи кнопки.
 */
private class Frecency(private val prefs: SharedPreferences) {
    fun recordUsed(key: String, now: Long = System.currentTimeMillis()) {
        val score = scoreAt(key, now) + 1.0
        prefs.edit { putString(KEY_PREFIX + key, "$score:$now") }
    }

    /** [keys] по убыванию score; при равенстве — их порядок. */
    fun ranking(
        keys: List<String>,
        now: Long = System.currentTimeMillis(),
    ): List<String> = keys.sortedByDescending { scoreAt(it, now) }

    private fun scoreAt(key: String, now: Long): Double {
        val stored = prefs.getString(KEY_PREFIX + key, null) ?: return 0.0
        val score = stored.substringBefore(':').toDoubleOrNull() ?: 0.0
        val updatedAt = stored.substringAfter(':').toLongOrNull() ?: now
        val elapsed = (now - updatedAt).coerceAtLeast(0)
        return score * 0.5.pow(elapsed.toDouble() / HALF_LIFE_MS)
    }

    private companion object {
        const val KEY_PREFIX = "frecency/"
        const val HALF_LIFE_MS = 21L * 24 * 60 * 60 * 1000
    }
}
