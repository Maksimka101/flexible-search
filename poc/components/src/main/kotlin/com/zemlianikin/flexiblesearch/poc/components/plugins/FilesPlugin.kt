package com.zemlianikin.flexiblesearch.poc.components.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.zemlianikin.flexiblesearch.poc.components.core.ActionIcon
import com.zemlianikin.flexiblesearch.poc.components.core.NameMatcher
import com.zemlianikin.flexiblesearch.poc.components.core.Plugin
import com.zemlianikin.flexiblesearch.poc.components.core.PluginScope
import com.zemlianikin.flexiblesearch.poc.components.core.ResultsBuilder
import com.zemlianikin.flexiblesearch.poc.components.core.materialIcon
import kotlinx.coroutines.delay

/**
 * Мок поиска по файлам: выдуманный список, нечёткий поиск по имени.
 * Картинки, PDF и видео получают «превью» с задержкой, как при чтении с
 * диска; остальные — плашку с расширением. `files ` — весь список.
 */
class FilesPlugin : Plugin {
    override val id = "files"

    private class MockFile(
        val name: String,
        val bytes: Long,
        val daysAgo: Int,
        val folder: String,
    ) {
        val extension = name.substringAfterLast('.', "").lowercase()
        val hasPreview = extension in setOf("jpg", "png", "pdf", "mp4")
    }

    private val files =
        listOf(
            MockFile("report-q3.pdf", 2_400_000, 1, "Documents"),
            MockFile("report-q3.xlsx", 88_000, 8, "Download"),
            MockFile("Annual report 2025.pdf", 5_100_000, 40, "Documents"),
            MockFile(
                "IMG_20261018_113340.jpg",
                3_200_000,
                2,
                "DCIM/Camera",
            ),
            MockFile(
                "IMG_20261012_190211.jpg",
                2_900_000,
                8,
                "DCIM/Camera",
            ),
            MockFile(
                "Screenshot_Pixel Search.png",
                640_000,
                2,
                "Screenshots",
            ),
            MockFile("Summer trip Kotor.mp4", 182_000_000, 60, "Movies"),
            MockFile("passport scan.pdf", 1_300_000, 300, "Documents"),
            MockFile("Договор аренды.pdf", 900_000, 120, "Download"),
            MockFile("Резюме 2026.docx", 64_000, 30, "Documents"),
            MockFile("Расходы октябрь.xlsx", 41_000, 3, "Documents"),
            MockFile("budget 2026.xlsx", 52_000, 15, "Documents"),
            MockFile("notes.txt", 4_000, 0, "Documents"),
            MockFile("search-app.md", 10_600, 2, "develop"),
            MockFile("telegram-desktop.zip", 48_000_000, 90, "Download"),
            MockFile("Podcast episode 42.mp3", 61_000_000, 12, "Podcasts"),
            MockFile("boarding pass TIV-BEG.pdf", 210_000, 5, "Download"),
            MockFile("photo-countries.apk", 11_000_000, 6, "Download"),
            MockFile(
                "wallpaper sveti stefan.jpg",
                4_700_000,
                25,
                "Pictures",
            ),
            MockFile("Invoice 1042.pdf", 180_000, 18, "Download"),
        )

    override fun install(scope: PluginScope) {
        val icon = ActionIcon.Vector(FolderIcon)
        val all =
            scope.trigger(
                "Files",
                "files",
                placeholder = "File name",
                icon = icon,
            )
        val context = scope.context
        scope.results(title = "Files", icon = icon, fullList = all) {
            found(context, it)
        }
        scope.results(all, title = "Files", icon = icon) { query ->
            // С префиксом и без текста — последние файлы.
            if (query.isBlank()) {
                for (file in files.sortedBy { it.daysAgo }) {
                    add(context, file, emptyList())
                }
            } else {
                found(context, query)
            }
        }
    }

    private suspend fun ResultsBuilder.found(
        context: Context,
        query: String,
    ) {
        delay(250) // как запрос к MediaStore
        for (ranked in NameMatcher.rank(query, files) { it.name }) {
            add(context, ranked.value, ranked.match.positions)
        }
    }

    private fun ResultsBuilder.add(
        context: Context,
        file: MockFile,
        highlights: List<Int>,
    ) {
        val size = Formatter.formatShortFileSize(context, file.bytes)
        val modified =
            DateUtils.getRelativeTimeSpanString(
                System.currentTimeMillis() -
                    file.daysAgo * DateUtils.DAY_IN_MILLIS,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
            )
        item(
            title = file.name,
            subtitle = "$size · $modified · ${file.folder}",
            titleHighlights = highlights,
            thumbnail = { thumbnail(file) },
            id = file.name,
        ) {
            Toast.makeText(
                    context,
                    "Mock: open ${file.name}",
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    private suspend fun thumbnail(file: MockFile): ImageBitmap {
        if (file.hasPreview)
            delay(200L + Math.floorMod(file.name.hashCode(), 400))
        val size = 120
        val bitmap =
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = Math.floorMod(file.name.hashCode(), 360).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (file.hasPreview) {
            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    android.graphics.Color.HSVToColor(
                        floatArrayOf(hue, 0.5f, 0.9f)
                    ),
                    android.graphics.Color.HSVToColor(
                        floatArrayOf(hue + 60, 0.7f, 0.5f)
                    ),
                    Shader.TileMode.CLAMP,
                )
        } else {
            paint.color = colorFor(file.extension)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 34f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
        canvas.drawText(
            file.extension.uppercase(),
            size / 2f,
            size / 2f + 12f,
            label,
        )
        return bitmap.asImageBitmap()
    }

    private fun colorFor(extension: String): Int =
        when (extension) {
            "docx" -> 0xFF2B579A
            "xlsx" -> 0xFF217346
            "mp3" -> 0xFFE65100
            "zip" -> 0xFF6D4C41
            "apk" -> 0xFF3DDC84
            else -> 0xFF607D8B
        }.toInt()
}

// Material Symbols «folder».
private val FolderIcon =
    materialIcon(
        "Files",
        "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16" +
            "c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z",
    )
