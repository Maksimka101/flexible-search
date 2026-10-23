package com.zemlianikin.flexiblesearch.plugins.converter.rates

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Офлайн-кэш курсов в одном текстовом файле: один снимок — один файл.
 * Формат — в `CachedRatesText.kt`. Заменить реализацию можно через
 * [RatesCache].
 *
 * Запись атомарная: во временный файл рядом и переименование. Чтение любой
 * битой версии даёт null, файл при этом не трогаем.
 */
class FileRatesCache(private val file: File) : RatesCache {

    override suspend fun load(): CachedRates? =
        withContext(Dispatchers.IO) {
            try {
                if (file.isFile)
                    decodeCachedRates(
                        file.readLines(StandardCharsets.UTF_8)
                    )
                else null
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun save(rates: CachedRates) {
        withContext(Dispatchers.IO) {
            val text = encodeCachedRates(rates)
            val dir = file.absoluteFile.parentFile
            dir?.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            try {
                tmp.writeText(text, StandardCharsets.UTF_8)
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                tmp.delete()
            }
        }
    }
}
