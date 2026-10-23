# Flexible search

Android-поиск в духе Pixel Search, где каждую часть UI и логики можно поменять плагином.
Заметки и решения — `search-app.md`, предыстория — `~/develop/self-modifying-apps.md`.
Сборка в Termux на телефоне.

## Структура
- `core/` — Android-библиотека: тонкое ядро, UI по умолчанию и API плагинов
  (внешние плагины подключат её `compileOnly`). Система компонентов (`Component`,
  `style`/`override`), DSL результатов, префиксы-триггеры с чипом, ряд быстрых
  действий и кнопки в строке поиска, виджеты пустой строки
  (`widget`/`widgetFlow`), экраны плагинов (`screen`, Navigation 3), `NameMatcher`
  (fuzzy + раскладка + транслит), диплинки `SearchLinks`, виджет строки
  поиска на главный экран и сервисы для плагинов в `PluginScope`:
  `prefs` (свой SharedPreferences), `http` (общий OkHttp с кэшем), `json`
  (kotlinx.serialization).
- `app/` — хост (`com.zemlianikin.flexiblesearch`) и встроенные плагины в `plugins/`
  (пишутся только через `PluginScope`, кроме виджетов главного экрана): поиск приложений
  (и по переводам названий), калькулятор и конвертер валют (`ca `, `=`;
  `plugins/converter/` — порт `~/develop/currency-converter` со своим нампадом),
  «искать в…», фонарик и кнопки. `SettingsPlugin` — невыключаемый
  плагин хоста: шестерёнка в строке и экран, где плагины выключаются. `plugins/mocks/` — один
  `MocksPlugin` с моками веба, файлов и погоды.
- `poc/<имя>` — эксперименты: отдельный модуль со своим `applicationId`, ставится
  рядом. Сейчас пусто.
- Общие версии — `gradle/libs.versions.toml`.

## Правила
- Ядро (`core/`) меняется только с разрешения: перед правкой
  объяснить, что и зачем менять и почему не обойтись плагином.
- Плагины, в том числе встроенные (`app/…/plugins/`), меняются как
  угодно без разрешения.

## Сборка
- `./gradlew spotlessApply` — форматирование Kotlin (ktfmt, kotlinlang style, 76 колонок —
  ширина текста в nvim на разложенном экране). Длинные строки и `code` в KDoc ktfmt не переносит
  аккуратно: строки бить вручную, примеры кода в KDoc — блоком ```.
- `./gradlew testDebugUnitTest` — тесты.
- Релиз — с R8; всё общее с плагинами держит `-keep` в `app/proguard-rules.pro`.
- `./gradlew :app:assembleDebug` — APK в `app/build/outputs/apk/debug/`, ставить `adbi <apk>`.
- Скриншот: `adb -s localhost:5555 exec-out screencap -p` пишет перед PNG предупреждение про несколько
  дисплеев — данные начинаются с `\x89PNG`.
  Без `-d` может снять чёрный неактивный дисплей: id — из
  `adb shell dumpsys SurfaceFlinger --display-id`, `screencap -d <id> -p`.
