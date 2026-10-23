# Flexible search

Android-поиск в духе Pixel Search, где каждую часть UI и логики можно поменять плагином.
Заметки и решения — `search-app.md`, предыстория — `~/develop/self-modifying-apps.md`.
Сборка в Termux на телефоне.

## Структура
- `core/` — Android-библиотека: тонкое ядро, UI по умолчанию и API плагинов
  (внешние плагины подключат её `compileOnly`). Система компонентов (`Component`,
  `style`/`override`), DSL результатов, префиксы-триггеры с чипом, ряд быстрых
  действий, виджеты пустой строки (`widget`/`widgetFlow`), `NameMatcher`
  (fuzzy + раскладка + транслит) и сервисы для плагинов в `PluginScope`:
  `prefs` (свой SharedPreferences), `http` (общий OkHttp с кэшем), `json`
  (kotlinx.serialization).
- `app/` — хост (`com.zemlianikin.flexiblesearch`) и встроенные плагины в `plugins/`
  (пишутся только через `PluginScope`): поиск приложений
  (и по переводам названий), калькулятор и конвертер валют (`ca `, `=`;
  `plugins/converter/` — порт `~/develop/currency-converter` со своим нампадом),
  «искать в…», строка-«таблетка», фонарик и кнопки. `plugins/mocks/` — один
  `MocksPlugin` с моками веба, файлов и погоды.
- `poc/<имя>` — эксперименты: отдельный модуль со своим `applicationId`, ставится
  рядом. Сейчас пусто.
- Общие версии — `gradle/libs.versions.toml`.

## Сборка
- `./gradlew spotlessApply` — форматирование Kotlin (ktfmt, kotlinlang style, 76 колонок —
  ширина текста в nvim на разложенном экране). Длинные строки и `code` в KDoc ktfmt не переносит
  аккуратно: строки бить вручную, примеры кода в KDoc — блоком ```.
- `./gradlew testDebugUnitTest` — тесты.
- `./gradlew :app:assembleDebug` — APK в `app/build/outputs/apk/debug/`, ставить `adbi <apk>`.
- Скриншот: `adb -s localhost:5555 exec-out screencap -p` пишет перед PNG предупреждение про несколько
  дисплеев — данные начинаются с `\x89PNG`.
  Без `-d` может снять чёрный неактивный дисплей: id — из
  `adb shell dumpsys SurfaceFlinger --display-id`, `screencap -d <id> -p`.
