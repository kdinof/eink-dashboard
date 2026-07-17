# M103 — Убрать демо-модули из runtime-дашборда

## Статус

completed (verified on-device, Meebook M103)

## Краткий результат

Демо-модули (`demo.clock`, `demo.agenda`) больше **не регистрируются в runtime ни в
одном build type**. Раньше `DashboardViewModel` добавлял их в реестр под
`if (BuildConfig.DEBUG)`, из-за чего debug-APK на M103 показывал лишние блоки
«Clock (демо)» и «Agenda» рядом с реальными данными. Теперь дашборд собирает
только пять продуктовых модулей — `calendar, todoist, clock, weather, battery`.

Демо-классы сохранены как **тест-фикстуры**: `RefreshCoordinatorTest` использует
`DemoClockModule` (`EveryMinute`) и `DemoAgendaModule` (`Periodic(15m)`), потому что
вдвоём они покрывают обе refresh-политики тривиальными, без источника данных,
телами `refresh()` — идеально для проверки планировщика на виртуальном времени.

## Изменения

- `app/src/main/java/com/eink/dashboard/dashboard/DashboardViewModel.kt`
  - удалена строка `.registerAll(if (BuildConfig.DEBUG) demoModules() else emptyList())`
    и импорт `demoModules`; реестр теперь всегда demo-free.
  - обновлён KDoc: только реальные модули регистрируются в любом build; `allowDemo =
    BuildConfig.DEBUG` оставлен, чтобы demo-safety guard оставался живым и покрытым тестами.
- `app/src/main/java/com/eink/dashboard/modules/demo/DemoModules.kt`
  - удалён неиспользуемый провайдер `demoModules()` (был нужен только реестру).
  - KDoc переписан: файл существует исключительно как фикстура `RefreshCoordinatorTest`,
    не регистрируется в runtime.
- `reports/T05_weather_system.md` — уточнена строка про порядок блоков (демо убраны).

Инфраструктура `isDemo` (флаг в `DashboardModule`, `allowDemo` guard реестра, DEMO-тег
в `ModuleBlock`, баннер в `DiagnosticsScreen`, `(demo)` в `SettingsScreen`,
`hasDemoModules`) оставлена намеренно — это защитный слой на случай будущего демо-модуля,
он по-прежнему проверяется `RefreshCoordinatorTest` (`allowDemo = true`) и модульными
тестами (`isDemo == false` для реальных модулей). Runtime-модуль с `isDemo == true`
больше не существует, поэтому ни один из этих UI-маркеров не активируется.

## Проверки

- `./scripts/check.sh` — зелёный: `testDebugUnitTest` + `testReleaseUnitTest` прошли,
  `assembleDebug` собрал APK. BUILD SUCCESSFUL.
- On-device (Meebook M103, serial redacted, API 30):
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` → Success.
  - Приложение запущено, `mResumedActivity = com.eink.dashboard.debug/.MainActivity`
    (resume OK).
  - `uiautomator dump`: **0 вхождений «demo»/«DEMO»**. Видимые блоки: Calendar, Todoist,
    Clock (12:05), Weather, Battery (100% charging) — только реальные модули.
  - Скриншот подтверждает пять блоков без DEMO-тегов.

## Что должен знать следующий агент

- Порядок блоков и их регистрация теперь целиком в `DashboardViewModel` без ветки debug.
- `DemoModules.kt` трогать только вместе с `RefreshCoordinatorTest`; в продакшн-flow он
  не участвует. R8 в release всё равно вырежет неиспользуемые демо-классы.
