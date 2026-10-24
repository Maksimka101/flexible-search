package com.zemlianikin.flexiblesearch.plugins

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.matching.NameMatcher
import com.zemlianikin.flexiblesearch.core.results.ResultItem
import com.zemlianikin.flexiblesearch.core.results.buildResults
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Приложения из лаунчера, включая рабочий профиль. Запуск закрывает поиск.
 * На пустой строке — все: недавно запущенные отсюда первыми, дальше по
 * алфавиту; ряд показывает, сколько влезет, остальные — прокруткой. Находит
 * и по названию на другом языке: `настройки` — Settings.
 */
class AppsPlugin : Plugin {
    override val id = "apps"
    override val name = "Apps"
    override val description = "Installed apps, also by translated name"

    override fun install(scope: PluginScope) {
        val index =
            AppIndex(scope.context, LabelCache(scope.prefs, scope.json))
        val recent = RecentApps(scope.prefs)

        suspend fun results(
            list: AppList,
            query: String,
        ): List<ResultItem> {
            val apps =
                if (query.isNotBlank()) list.search(query)
                else {
                    val recentApps = list.find(recent.keys())
                    recentApps + (list.sorted() - recentApps.toSet())
                }
            return buildResults {
                for (app in apps) {
                    app(app.label, { index.icon(app) }, id = app.key) {
                        recent.add(app.key)
                        index.launch(app)
                        close()
                    }
                }
            }
        }

        // Список перечитывается при каждом открытии: ничего не пропустит,
        // даже если система не сообщила об изменении.
        scope.onOpen { index.reload(ifIdle = true) }
        scope.results.addFlow(onEmpty = true) { query ->
            flow {
                // Сразу — прежний список, потом — перечитанный, если он
                // ещё читается.
                val cached = index.cached
                if (cached != null) emit(results(cached, query))
                val fresh = index.fresh()
                if (fresh !== cached) emit(results(fresh, query))
            }
        }
    }
}

/** Снимок установленных; переводы названий догружаются в фоне. */
private class AppList(val apps: List<InstalledApp>, val translated: Job) {
    suspend fun search(query: String): List<InstalledApp> {
        // Ждёт только запрос, набранный раньше, чем готовы переводы.
        translated.join()
        return NameMatcher.rankByNames(query, apps) {
                listOf(it.label) + it.translations
            }
            .map { it.value }
    }

    /** Удалённые пропускаются. */
    fun find(keys: List<String>): List<InstalledApp> {
        val byKey = apps.associateBy { it.key }
        return keys.mapNotNull { byKey[it] }
    }

    /** Все по названию, без учёта регистра и с языком системы. */
    fun sorted(): List<InstalledApp> {
        val collator = Collator.getInstance()
        return apps.sortedWith(compareBy(collator) { it.label })
    }
}

private class InstalledApp(
    val label: String,
    val info: LauncherActivityInfo,
    /** Версия названий: APK и языки системы, см. [LabelCache]. */
    val stamp: String,
) {
    val key = keyOf(info)

    /** Название на других языках, без [label]; заполняется в фоне. */
    @Volatile var translations: List<String> = emptyList()
}

private fun keyOf(info: LauncherActivityInfo) =
    "${info.componentName.flattenToShortString()}@${info.user}"

/**
 * Названия и переводы с прошлого раза: читать их из ресурсов каждого
 * приложения на холодном старте — сотни миллисекунд. Запись верна, пока тот
 * же APK (при обновлении путь меняется) и те же языки системы.
 */
private class LabelCache(
    private val prefs: SharedPreferences,
    private val json: Json,
) {
    @Serializable
    class Entry(
        val stamp: String,
        val label: String,
        val translations: List<String>,
    )

    fun load(): Map<String, Entry> =
        try {
            prefs
                .getString(KEY, null)
                ?.let { json.decodeFromString<Map<String, Entry>>(it) }
                .orEmpty()
        } catch (e: SerializationException) {
            emptyMap()
        }

    fun save(apps: List<InstalledApp>) {
        val entries = apps.associate {
            it.key to Entry(it.stamp, it.label, it.translations)
        }
        prefs.edit { putString(KEY, json.encodeToString(entries)) }
    }

    private companion object {
        const val KEY = "labels"
    }
}

/** Последние запущенные, новые первыми. */
private class RecentApps(private val prefs: SharedPreferences) {
    fun keys(): List<String> =
        prefs.getString(KEY, null)?.split('\n').orEmpty()

    fun add(key: String) {
        val keys = (listOf(key) + (keys() - key)).take(LIMIT)
        prefs.edit { putString(KEY, keys.joinToString("\n")) }
    }

    companion object {
        /** Сколько недавних помнить; дальше — по алфавиту. */
        const val LIMIT = 50
        private const val KEY = "recent"
    }
}

private class AppIndex(
    private val context: Context,
    private val labels: LabelCache,
) {
    private val launcherApps =
        context.getSystemService(LauncherApps::class.java)
    private val iconSize =
        (48 * context.resources.displayMetrics.density).toInt()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val mutex = Mutex()
    private val background =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Последний прочитанный список; null — ещё ни одного. */
    @Volatile
    var cached: AppList? = null
        private set

    @Volatile private var loading: Deferred<AppList>? = null

    init {
        // Установили, удалили или обновили приложение — перечитать список
        // и значки.
        launcherApps.registerCallback(
            object : LauncherApps.Callback() {
                override fun onPackageAdded(
                    packageName: String,
                    user: UserHandle,
                ) = invalidate()

                override fun onPackageRemoved(
                    packageName: String,
                    user: UserHandle,
                ) = invalidate()

                override fun onPackageChanged(
                    packageName: String,
                    user: UserHandle,
                ) = invalidate()

                override fun onPackagesAvailable(
                    names: Array<String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) = invalidate()

                override fun onPackagesUnavailable(
                    names: Array<String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) = invalidate()
            }
        )
    }

    private fun invalidate() {
        icons.clear()
        reload()
    }

    /**
     * Перечитать в фоне; пока читается, [cached] — прежний. [ifIdle] — не
     * начинать, если уже читается: на холодном старте первый запрос
     * опережает открытие окна.
     */
    fun reload(ifIdle: Boolean = false): Deferred<AppList> =
        loading?.takeIf { ifIdle && it.isActive }
            ?: background
                .async { mutex.withLock { read() } }
                .also { loading = it }

    /** Список после идущего перечитывания; не читался — прочитать. */
    suspend fun fresh(): AppList = (loading ?: reload()).await()

    suspend fun icon(app: InstalledApp): ImageBitmap =
        icons[app.key]
            ?: withContext(Dispatchers.IO) {
                    // Значок рабочего профиля рисует сама система.
                    app.info
                        .getBadgedIcon(0)
                        .toBitmap(iconSize, iconSize)
                        .asImageBitmap()
                }
                .also { icons[app.key] = it }

    fun launch(app: InstalledApp) {
        try {
            launcherApps.startMainActivity(
                app.info.componentName,
                app.info.user,
                null,
                null,
            )
        } catch (e: Exception) {
            Log.e("AppsPlugin", "Can't launch ${app.key}", e)
        }
    }

    private fun read(): AppList {
        val saved = labels.load()
        val locales = LocaleList.getDefault().toLanguageTags()
        val apps =
            launcherApps.profiles
                .flatMap { user ->
                    launcherApps.getActivityList(null, user)
                }
                .filter {
                    it.componentName.packageName != context.packageName
                }
                .map { info ->
                    val stamp = "${info.applicationInfo.sourceDir}|$locales"
                    val entry =
                        saved[keyOf(info)]?.takeIf { it.stamp == stamp }
                    InstalledApp(
                            entry?.label ?: info.label.toString(),
                            info,
                            stamp,
                        )
                        .apply {
                            entry?.let { translations = it.translations }
                        }
                }
        val stale = apps.filter { saved[it.key]?.stamp != it.stamp }
        val translated = background.launch {
            translate(stale)
            if (stale.isNotEmpty() || saved.size != apps.size) {
                labels.save(apps)
            }
        }
        return AppList(apps, translated).also { cached = it }
    }

    /**
     * Названия берутся из ресурсов самого приложения: языки системы плюс
     * [TRANSLATION_LOCALES]. Нет перевода — нет и имени.
     */
    private fun translate(apps: List<InstalledApp>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val start = SystemClock.elapsedRealtime()
        val system = LocaleList.getDefault()
        val locales =
            (List(system.size()) { system[it] } + TRANSLATION_LOCALES)
                .distinctBy { it.language }
        for (group in
            apps.groupBy { it.info.applicationInfo.packageName }.values) {
            try {
                translate(group, locales)
            } catch (e: Exception) {
                Log.w("AppsPlugin", "Can't translate ${group[0].key}", e)
            }
        }
        Log.i(
            "AppsPlugin",
            "Translated ${apps.size} apps in " +
                "${SystemClock.elapsedRealtime() - start} ms",
        )
    }

    /** [apps] — активности одного пакета: ресурсы грузятся один раз. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun translate(apps: List<InstalledApp>, locales: List<Locale>) {
        val application = apps[0].info.applicationInfo
        val resources = locales.map { locale ->
            val config =
                Configuration(context.resources.configuration).apply {
                    setLocale(locale)
                }
            context.packageManager.getResourcesForApplication(
                application,
                config,
            )
        }
        for (app in apps) {
            // Своего названия у активности нет — берётся название
            // приложения, как и в лаунчере.
            val label =
                app.info.activityInfo.labelRes.takeIf { it != 0 }
                    ?: application.labelRes
            if (label == 0) continue
            app.translations =
                resources
                    .map { it.getString(label) }
                    .filter { !it.equals(app.label, ignoreCase = true) }
                    .distinct()
        }
    }

    private companion object {
        /** Под них есть раскладка и транслит ([NameMatcher]). */
        val TRANSLATION_LOCALES =
            listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"))
    }
}
