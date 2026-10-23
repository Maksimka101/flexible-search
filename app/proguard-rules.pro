# Общее с плагинами. Внешние плагины грузятся через DexClassLoader и
# вызывают классы хоста напрямую; R8 этих вызовов не видит и вырезал бы
# неиспользуемое хостом (NoSuchMethodError в плагине). Список — всё, что
# core отдаёт как api, с транзитивными типами в сигнатурах.

# API плагинов.
-keep class com.zemlianikin.flexiblesearch.core.** { *; }

# Kotlin и корутины.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Compose и то, что видно в его API.
-keep class androidx.compose.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.lifecycle.** { *; }

# Сеть и JSON.
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class kotlinx.serialization.** { *; }
