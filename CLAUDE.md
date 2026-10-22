# Flexible search

Android-поиск в духе Pixel Search, где каждую часть UI и логики можно поменять плагином.
Заметки и решения — `search-app.md`, предыстория — `~/develop/self-modifying-apps.md`.
Сборка в Termux на телефоне.

## Структура
- `poc/` — proof of concept, каждый — отдельный Android-модуль со своим `applicationId`,
  ставятся рядом. Общие версии — `gradle/libs.versions.toml`.
  - `poc/components` — система компонентов (`Component`, `style`/`override`), DSL результатов,
    префиксы-триггеры с чипом, ряд быстрых действий, встроенные плагины: поиск приложений,
    калькулятор (`calc `, `=`), «искать в…», моки веба и файлов, строка-«таблетка»,
    виджеты пустой строки (`widget`/`widgetFlow`): фонарик и кнопки, мок погоды.
    В core также `NameMatcher` (fuzzy + раскладка + транслит) и сервисы для плагинов в `PluginScope`:
    `prefs` (свой SharedPreferences), `http` (общий OkHttp с кэшем), `json` (kotlinx.serialization).
    `core/` — тонкое ядро и UI по умолчанию, `plugins/` — плагины (пишутся только через `PluginScope`).

## Сборка
- `./gradlew spotlessApply` — форматирование Kotlin (ktfmt, kotlinlang style, 76 колонок —
  ширина текста в nvim на разложенном экране). Длинные строки и `code` в KDoc ktfmt не переносит
  аккуратно: строки бить вручную, примеры кода в KDoc — блоком ```.
- `./gradlew :poc:components:testDebugUnitTest` — тесты.
- `./gradlew :poc:components:assembleDebug` — APK в `poc/components/build/outputs/apk/debug/`, ставить `adbi <apk>`.
- Скриншот: `adb -s localhost:5555 exec-out screencap -p` пишет перед PNG предупреждение про несколько
  дисплеев — данные начинаются с `\x89PNG`.
