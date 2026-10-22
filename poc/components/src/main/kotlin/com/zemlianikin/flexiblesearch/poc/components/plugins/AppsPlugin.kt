package com.zemlianikin.flexiblesearch.poc.components.plugins

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import com.zemlianikin.flexiblesearch.poc.components.core.NameMatcher
import com.zemlianikin.flexiblesearch.poc.components.core.Plugin
import com.zemlianikin.flexiblesearch.poc.components.core.PluginScope
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Приложения из лаунчера, включая рабочий профиль. Запуск закрывает поиск.
 * На пустой строке — недавно запущенные отсюда.
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

    @Volatile private var apps: List<InstalledApp>? = null

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

    suspend fun search(query: String, limit: Int): List<InstalledApp> =
        NameMatcher.rank(query, all()) { it.label }
            .take(limit)
            .map { it.value }

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
                        .also { apps = it }
            }
}
