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
import com.zemlianikin.flexiblesearch.core.NameMatcher
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Приложения из лаунчера, включая рабочий профиль. Запуск закрывает поиск.
 * На пустой строке — недавно запущенные отсюда. Находит и по названию на
 * другом языке: `настройки` — Settings.
 */
class AppsPlugin : Plugin {
    override val id = "apps"

    override fun install(scope: PluginScope) {
        val index = AppIndex(scope.context)
        val recent = RecentApps(scope.prefs)
        scope.results(onEmpty = true) { query ->
            val apps =
                if (query.isBlank()) index.find(recent.keys())
                else index.search(query, limit = RecentApps.LIMIT)
            for (app in apps) {
                app(app.label, index.icon(app), id = app.key) {
                    recent.add(app.key)
                    index.launch(app)
                    close()
                }
            }
        }
    }
}

private class InstalledApp(
    val label: String,
    val info: LauncherActivityInfo,
) {
    val key = "${info.componentName.flattenToShortString()}@${info.user}"

    /** Название на других языках, без [label]; заполняется в фоне. */
    @Volatile var translations: List<String> = emptyList()
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
        /** Сколько плиток в ряду приложений. */
        const val LIMIT = 5
        private const val KEY = "recent"
    }
}

private class AppIndex(private val context: Context) {
    private val launcherApps =
        context.getSystemService(LauncherApps::class.java)
    private val iconSize =
        (48 * context.resources.displayMetrics.density).toInt()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val mutex = Mutex()
    private val background =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var apps: List<InstalledApp>? = null
    @Volatile private var translating: Job? = null

    init {
        // Установили, удалили или обновили приложение — перечитать список
        // при следующем запросе.
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
        apps = null
        icons.clear()
    }

    suspend fun search(query: String, limit: Int): List<InstalledApp> {
        val apps = all()
        // Переводы начинают грузиться при открытии окна; ждёт только
        // первый запрос, если набран раньше, чем они готовы.
        translating?.join()
        return NameMatcher.rankByNames(query, apps) {
                listOf(it.label) + it.translations
            }
            .take(limit)
            .map { it.value }
    }

    /** Удалённые пропускаются. */
    suspend fun find(keys: List<String>): List<InstalledApp> {
        val byKey = all().associateBy { it.key }
        return keys.mapNotNull { byKey[it] }
    }

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

    private suspend fun all(): List<InstalledApp> =
        apps
            ?: mutex.withLock {
                apps
                    ?: withContext(Dispatchers.IO) {
                            launcherApps.profiles
                                .flatMap { user ->
                                    launcherApps.getActivityList(null, user)
                                }
                                .filter {
                                    it.componentName.packageName !=
                                        context.packageName
                                }
                                .map {
                                    InstalledApp(it.label.toString(), it)
                                }
                        }
                        .also {
                            apps = it
                            translating = background.launch {
                                translate(it)
                            }
                        }
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
