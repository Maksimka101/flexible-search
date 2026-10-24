package com.zemlianikin.flexiblesearch.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Icon
import android.os.Build
import com.zemlianikin.flexiblesearch.core.PluginManager
import com.zemlianikin.flexiblesearch.core.R
import com.zemlianikin.flexiblesearch.core.buttons.ActionIcon
import com.zemlianikin.flexiblesearch.core.results.Action
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Ярлык плагина: в меню по долгому нажатию на иконку приложения, оттуда или
 * через [PluginLauncher.pin] — на рабочий стол. См.
 * [PluginLauncher.shortcut].
 */
class Shortcut(
    val id: String,
    val label: String,
    val icon: ActionIcon,
    val action: Action,
)

/** Ярлыки плагинов в [ShortcutManager]. */
internal object Shortcuts {
    // Хост собирается в главном потоке, как и в окне поиска.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Меню по долгому нажатию — ярлыки включённых плагинов по порядку.
     * Закреплённые на рабочем столе остаются и у выключенного плагина, но
     * серые; включили — снова работают.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watch(context: Context, plugins: PluginManager) {
        val app = context.applicationContext
        scope.launch {
            plugins.disabled
                .flatMapLatest { plugins.host(it).shortcuts }
                .distinctUntilChanged()
                .collectLatest { publish(app, it) }
        }
    }

    fun canPin(context: Context): Boolean =
        context
            .getSystemService(ShortcutManager::class.java)
            .isRequestPinShortcutSupported

    /** Лаунчер спросит, добавить ли ярлык на рабочий стол. */
    fun pin(context: Context, shortcut: Shortcut) {
        val app = context.applicationContext
        scope.launch {
            val info = info(app, shortcut, rank = 0) ?: return@launch
            app.getSystemService(ShortcutManager::class.java)
                .requestPinShortcut(info, null)
        }
    }

    private suspend fun publish(
        context: Context,
        shortcuts: List<Shortcut>,
    ) {
        val manager = context.getSystemService(ShortcutManager::class.java)
        val infos = shortcuts.mapIndexedNotNull { rank, shortcut ->
            info(context, shortcut, rank)
        }
        manager.dynamicShortcuts =
            infos.take(manager.maxShortcutCountPerActivity)
        // Закреплённым — новые название и иконку.
        manager.updateShortcuts(infos)
        val registered = infos.mapTo(HashSet()) { it.id }
        val (enabled, disabled) =
            manager.pinnedShortcuts
                .map { it.id }
                .partition { it in registered }
        manager.enableShortcuts(enabled)
        manager.disableShortcuts(disabled, "Plugin is turned off")
    }

    private suspend fun info(
        context: Context,
        shortcut: Shortcut,
        rank: Int,
    ): ShortcutInfo? {
        val icon = icon(context, shortcut.icon) ?: return null
        val intent =
            Intent(Intent.ACTION_VIEW, SearchLinks.shortcut(shortcut.id))
                .setPackage(context.packageName)
        return ShortcutInfo.Builder(context, shortcut.id)
            .setShortLabel(shortcut.label)
            .setIcon(icon)
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    /**
     * Вектор — знак на круге цветов Material You, как ярлыки системных
     * приложений; картинка — как есть.
     */
    private suspend fun icon(context: Context, icon: ActionIcon): Icon? {
        val density = context.resources.displayMetrics.density
        val glyph = (GLYPH_DP * density).roundToInt()
        val bitmap = icon.toBitmap(glyph) ?: return null
        if (icon !is ActionIcon.Vector) return Icon.createWithBitmap(bitmap)
        val (background, foreground) = colors(context)
        val size = (ADAPTIVE_DP * density).roundToInt()
        val adaptive =
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(adaptive)
        canvas.drawColor(background)
        val offset = (size - glyph) / 2f
        val paint =
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter =
                    PorterDuffColorFilter(
                        foreground,
                        PorterDuff.Mode.SRC_IN,
                    )
            }
        canvas.drawBitmap(bitmap, offset, offset, paint)
        return Icon.createWithAdaptiveBitmap(adaptive)
    }

    // Цвета запоминаются в иконке: сменили обои — обновятся при следующей
    // публикации, на старте процесса.
    private fun colors(context: Context): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getColor(android.R.color.system_accent1_100) to
                context.getColor(android.R.color.system_accent1_800)
        else
            context.getColor(R.color.shortcut_background) to
                context.getColor(R.color.shortcut_icon)

    /** Слой адаптивной иконки и знак на нём, как у системных ярлыков. */
    private const val ADAPTIVE_DP = 108
    private const val GLYPH_DP = 40
}
