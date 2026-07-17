# T02 — Платформа дашборда и e-ink-safe UI shell

## Статус

completed

## Краткий результат

Поверх фундамента T01 построен общий Dashboard shell: single-activity +
immersive fullscreen, замороженный module SPI (`DashboardModule`, `ModuleState`,
`RefreshPolicy`, `RefreshReason`, `DashboardModuleRegistry`), foreground-only
refresh coordinator с минутным тикером (без секунд), DataStore-настройки
(ориентация / keep-screen-on / видимость блоков), grayscale-тема с отключёнными
ripple/анимациями и три базовых экрана (Dashboard / Settings / Diagnostics).
Layout для двух ориентаций вынесен в чистую функцию с golden-baseline'ами; логика
scheduler/lifecycle покрыта 22 новыми unit-тестами (всего 27, все зелёные).
Демо-модули (`demo.clock`, `demo.agenda`) явно помечены как demo и регистрируются
**только в debug** — в release реестр пуст, поэтому образцовые данные не могут
попасть в сборку как реальные. Продуктовые модули (Calendar/Todoist/Weather) не
реализованы — это область T03–T05; T02 отдаёт им контракт подключения.

## Что реализовано (обязательные действия карточки)

| Требование карточки | Где |
| --- | --- |
| single Activity + immersive fullscreen | `MainActivity.kt` (`WindowInsetsControllerCompat.hide(systemBars)`) |
| `DashboardModule`, `ModuleState`, `RefreshPolicy`, `RefreshReason` | `dashboard/DashboardModule.kt`, `ModuleState.kt`, `RefreshPolicy.kt`, `RefreshReason.kt` |
| `DashboardModuleRegistry` | `dashboard/DashboardModuleRegistry.kt` (Builder + demo-gate) |
| foreground-only refresh coordinator | `dashboard/RefreshCoordinator.kt` + чистая `RefreshDecision.kt` |
| минутный ticker без секунд | `core/time/MinuteTicker.kt` (эмит только на границе `:00`) |
| стоп в background + немедленный refresh в `onResume` | `RefreshCoordinator.start()/stop()`, вызовы из `MainActivity.onResume/onPause` |
| портретный и альбомный шаблоны | `dashboard/DashboardLayoutSpec.kt` (1 колонка / 2 колонки) + `ui/DashboardScreen.kt` |
| grayscale design tokens | `dashboard/theme/EinkTokens.kt` (`EinkPalette`, `EinkSpacing`) |
| отключение анимаций, ripple, переходов | `theme/EinkTheme.kt` (`NoIndication` через `LocalIndication`), навигация plain `when` без crossfade, кастомные не-Material контролы |
| DataStore-настройки ориентации/keep-on/видимости | `settings/DashboardSettings.kt`, `settings/SettingsStore.kt` |
| базовые экраны Dashboard/Settings/Diagnostics | `ui/DashboardHost.kt`, `ui/DashboardScreen.kt`, `settings/ui/SettingsScreen.kt`, `diagnostics/ui/DiagnosticsScreen.kt` |
| fake/demo modules, помеченные demo | `modules/demo/DemoModules.kt` (`isDemo = true`, DEMO-бейдж, release-gate) |

## Module contract (SPI) и как подключить новый модуль — для T03–T05

Контракт заморожен. Модуль реализует интерфейс `DashboardModule`:

```kotlin
// modules/calendar/CalendarModule.kt   (владелец: T03)
class CalendarModule(
    private val repo: CalendarRepository,     // своя реализация данных
) : DashboardModule {

    override val id = "calendar"              // стабильный уникальный ключ (ключ настроек + diagnostics)
    override val title = "Calendar"
    override val refreshPolicy = RefreshPolicy.Periodic(15.minutes)
    // override val isDemo = false             // по умолчанию false — реальный модуль

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    // Вызывается координатором НА ФОНОВОМ dispatcher'е. Отменяемо.
    override suspend fun refresh(reason: RefreshReason) {
        try {
            val events = repo.loadToday()      // сеть/БД тут
            _state.value = if (events.isEmpty()) ModuleState.Empty()
                           else ModuleState.Ok(lastUpdatedEpochMs = System.currentTimeMillis())
            // свои данные модуль хранит у себя и рендерит в Content()
        } catch (e: IOException) {
            _state.value = ModuleState.Error("No network", lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs)
        }
    }

    @Composable
    override fun Content(modifier: Modifier) { /* только тело блока, grayscale, без своих таймеров */ }
}
```

Регистрация (единая точка — `DashboardViewModel`):

```kotlin
DashboardModuleRegistry.builder(allowDemo = BuildConfig.DEBUG)
    .register(CalendarModule(...))   // T03
    .register(TodoistModule(...))    // T04
    .register(WeatherModule(...))    // T05
    .build()
```

Порядок регистрации = канонический порядок блоков в сетке
(`DashboardLayoutSpec` раскладывает их row-major). Оболочка сама рисует рамку,
заголовок, статус (`updated HH:mm` / `stale` / ошибка) и DEMO-бейдж — модуль
рисует **только своё тело**.

Ключевые правила для реализаторов:
- **Модуль не заводит собственный периодический цикл.** Всю каденцию задаёт
  координатор через `refreshPolicy`; свой цикл продолжит работать в background и
  нарушит foreground-only-гарантию.
- `refresh()` обязан быть отменяемым (при уходе в background координатор
  отменяет in-flight refresh).
- `Content()` — чистый grayscale, без анимаций и без своих таймеров, читаем в обеих
  ориентациях.
- `id` стабилен: это ключ настройки видимости и подпись в Diagnostics.

## Правила file ownership для параллельной волны T03–T05

- **Заморожено (владелец T02, не менять):** весь пакет `dashboard/*` (контракты,
  registry, coordinator, layout, theme, shell-UI), `core/time/*`, `settings/*`.
  Это общие contracts из раздела 6 плана — после T02 считаются замороженными.
- **T03** владеет `modules/calendar/**` (+ `READ_CALENDAR` в манифесте).
- **T04** владеет `modules/todoist/**` (+ `INTERNET`).
- **T05** владеет `modules/weather/**` и системными модулями (clock/battery).
- Точки, которые каждая задача трогает совместно и потому требуют аккуратного
  merge: (1) **регистрация** своего модуля в `DashboardViewModel` (одна строка
  `.register(...)`); (2) добавление своих зависимостей в
  `gradle/libs.versions.toml`; (3) свои permissions в `AndroidManifest.xml`.
  Всё это — аддитивные вставки, конфликт маловероятен; при одном рабочем каталоге
  (без worktree) T03–T05 выполнять последовательно (см. §5 плана).
- `modules/demo/**` — временный, удаляется/отключается по мере появления реальных
  модулей; в release он и так не регистрируется.

## Тесты (scheduler/lifecycle + layout + settings)

Всего 27 unit-тестов, все проходят (`testDebug` + `testRelease`). Новые в T02 — 22:

- `core/time/MinuteTickerTest` (4) — чистая математика границы минуты (в т.ч. что
  на границе возвращается полная минута, а не 0) + эмиссия потока под виртуальным
  временем `runTest` (тики ровно на `60_000/120_000/180_000`).
- `dashboard/RefreshDecisionTest` (5) — матрица reason × policy: lifecycle-причины
  всегда обновляют; `MINUTE_TICK` гейтится политикой (Manual — никогда,
  EveryMinute — всегда, Periodic — по интервалу).
- `dashboard/RefreshCoordinatorTest` (3) — жизненный цикл на виртуальном времени:
  `start` (INITIAL форсит всё) → тик (обновляется только EveryMinute) → `stop`
  (после долгого ожидания **ни одного** refresh) → повторный `start` (RESUMED
  форсит всё); отдельно — periodic обновляется на тике после интервала; manual
  форсит periodic-модуль.
- `dashboard/DashboardLayoutSpecTest` (4) — **golden-baseline'ы** обеих ориентаций
  + короткий последний ряд + пустой дашборд.
- `settings/DashboardSettingsTest` (3) — логика видимости (по умолчанию всё видно,
  скрытие фильтрует, порядок сохраняется).
- `settings/SettingsDecodeTest` (3) — чистый маппинг `Preferences → DashboardSettings`
  (дефолты при пустом сторе, полный набор, откат неизвестного значения enum).

## Screenshot / golden baselines для двух ориентаций

Вместо хрупкого пиксельного диффа baseline'ы сделаны как детерминированный
текстовый рендер чистой раскладки (`DashboardLayoutSpec.render`), закоммичены и
сравниваются в тестах:

- `app/src/test/resources/golden/dashboard_portrait.txt` — 1 колонка, 4 строки.
- `app/src/test/resources/golden/dashboard_landscape.txt` — 2 колонки, 2 строки.

Регрессия раскладки видна как читаемый текст-дифф, без устройства и без флейки
image-diff. Регенерация — из docstring теста.

## Обязательные проверки

- `./scripts/check.sh` — **passed** (JDK 17 / SDK автопоиск; `test` +
  `assembleDebug`; unit-тесты 27/27, `app-debug.apk` собран).
- `./gradlew testDebugUnitTest` / `testReleaseUnitTest` — **passed**.
- Установка `app-debug.apk` на Meebook M103 — **passed** (`adb install -r` →
  Success).
- Запуск на устройстве — **passed**: `ResumedActivity = com.eink.dashboard.debug/
  .MainActivity`, процесс жив (pid 2244), crash-буфер по нашему пакету чист, в
  logcat — `EinkManager` + `SurfaceFlinger SET_EINK_MODE repaint` на нашем окне
  (панель реально перерисовывается).
- demo-state в release — **проверено кодом+тестом**: `allowDemo = BuildConfig.DEBUG`,
  Builder бросает на demo-модуль в non-debug; release-реестр пуст.
- минутный ticker без секунд, стоп в background, немедленный refresh в onResume —
  **проверено unit-тестами** `RefreshCoordinatorTest`/`MinuteTickerTest` (логика),
  проводка в Activity — `onResume→start`, `onPause→stop`.
- **портрет/ландшафт и sleep/wake на M103 — прогнаны физически на устройстве**
  (см. раздел ниже).

## Физические проверки на устройстве (portrait / landscape + sleep/wake)

Прогнаны через ADB на панели M103. Исходные системные настройки сохранены до и
восстановлены после (идентификаторы устройства не приводятся).

### Поворот portrait ↔ landscape

Исходные значения сохранены: `accelerometer_rotation=1`, `user_rotation=0`.
Отключён auto-rotate, затем поочерёдно форсированы обе ориентации:

```
# отключить авто-поворот, чтобы user_rotation вступил в силу
adb shell settings put system accelerometer_rotation 0

# --- ПОРТРЕТ ---
adb shell settings put system user_rotation 0
adb shell dumpsys window displays | grep cur=          # cur=1404x1872  (W<H)
adb shell dumpsys window | grep -oE "(land|port) finger"  # port finger
adb shell dumpsys activity activities | grep ResumedActivity
#   -> com.eink.dashboard.debug/.MainActivity  (resumed, pid 2244, crash-буфер чист)

# --- ЛАНДШАФТ ---
# внутренний e-ink дисплей по умолчанию игнорирует user_rotation, поэтому
# однократно включаем fix-to-user-rotation (потом выключаем при restore)
adb shell cmd window set-fix-to-user-rotation enabled
adb shell settings put system user_rotation 1
adb shell dumpsys window | grep mRotation=             # mRotation=1
adb shell dumpsys window displays | grep cur=          # cur=1872x1404  (W>H)
adb shell dumpsys window | grep -oE "(land|port) finger"  # land finger  (sw936dp w1248dp h881dp)
adb shell dumpsys activity activities | grep ResumedActivity
#   -> com.eink.dashboard.debug/.MainActivity  (resumed, pid 2244, crash-буфер чист)
```

Результат: панель реально переключается 1404×1872 (`rotation 0`, config `port`,
`w936dp`) ↔ 1872×1404 (`rotation 1`, config `land`, `w1248dp`). Оба перехода —
**MainActivity остаётся resumed, тот же pid (activity не пересоздаётся благодаря
`configChanges=orientation|screenSize|screenLayout|...`), crash-буфер по нашему
пакету пуст, FATAL/Exception/ANR по процессу нет.** Наблюдение по железу: внутренний
e-ink дисплей по умолчанию не honor'ит `user_rotation` (единственный display-mode,
vendor пинит rotation 0); ландшафт включается либо `set-fix-to-user-rotation`, либо
запросом активности `SCREEN_ORIENTATION_LANDSCAPE` — что и делает наш orientation-lock
из Settings.

### Sleep / wake

```
adb shell input keyevent 223            # KEYCODE_SLEEP
adb shell dumpsys power | grep mWakefulness   # mWakefulness=Asleep
adb shell pidof com.eink.dashboard.debug      # 2244 (процесс жив)
# crash-буфер по нашему пакету — пуст

adb shell input keyevent 224            # KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell dumpsys power | grep mWakefulness   # mWakefulness=Awake
adb shell dumpsys activity activities | grep ResumedActivity
#   -> mResumedActivity com.eink.dashboard.debug/.MainActivity
adb shell pidof com.eink.dashboard.debug      # 2244 (тот же pid — без рестарта)
# logcat: EinkManager fullRefreshNoDelay  (панель перерисовалась на wake)
```

Результат: при sleep процесс жив, крэшей нет; при wake+unlock приложение
восстанавливается — **`Awake`, MainActivity снова resumed, тот же pid (без
рестарта/крэша), панель делает full refresh.** Экран оставлен включённым.

### Восстановление настроек

```
adb shell cmd window set-fix-to-user-rotation disabled
adb shell settings put system user_rotation 0          # исходное
adb shell settings put system accelerometer_rotation 1 # исходное
# verify: accelerometer_rotation=1, user_rotation=0, mFixedToUserRotation=false
```

Все изменённые системные настройки возвращены к исходным значениям; финально —
`Awake`, MainActivity resumed, процесс жив.

## Известные ограничения

- **Пиксельный скриншот с устройства недоступен.** `adb screencap` на этом Rockchip
  e-ink возвращает полностью чёрный кадр (waveform-фреймбуфер панели не читается
  штатным путём), а `uiautomator dump` в immersive-режиме отдаёт окно vendor-
  wallpaper. Поэтому on-device проверка ограничена уровнем WM/lifecycle
  (rotation/config/resumed/pid/crash-буфер + события перерисовки панели), а не
  пиксельным сравнением; за «визуальный» baseline отвечают golden-файлы раскладки.
  Поворот portrait/landscape и sleep/wake **прогнаны физически через ADB** (раздел
  «Физические проверки») — подтверждены сменой rotation/config, сохранением resumed
  и отсутствием крэшей; именно пиксельный скрин снять нельзя.
- **Instrumented (androidTest) Compose-UI тесты не запускались.** Для T02 логика
  вынесена в чистые/coroutine-тестируемые слои; UI-инструменты — при желании в
  отдельной задаче.
- `assembleRelease`/R8 на release-варианте не прогонялись (release signing — T09);
  `compileRelease*` и `testReleaseUnitTest` — зелёные.
- Ripple полностью снят через app-wide `LocalIndication = NoIndication`;
  интерактивные контролы (nav/toggle) сделаны на `clickable`+border вместо Material
  `Button`/`Switch`, чтобы не тянуть версионно-зависимый `RippleTheme` API и убрать
  thumb-анимацию Switch.

## Риски и технический долг

- `DashboardViewModel` — композиционный корень без DI (по ADR-0001). Когда T03–T05
  добавят модули с зависимостями (repo/retrofit/room), сборку графа держать здесь;
  при росте — рассмотреть ручной AppContainer.
- Каденция `RefreshPolicy.Periodic` округляется до минут (тики только на границе
  минуты) — для weather/todoist это ок; если понадобится под-минутная точность,
  это осознанно не поддерживается (e-ink).
- Демо-модули используют `java.time` в `refresh()`; при реальных модулях выносить
  форматирование/таймзону в общий util (`core/time/TimeFormat` уже есть).

## Что должен знать следующий агент (T03)

- Контракт `DashboardModule` и регистрация — примеры выше. `id="calendar"`,
  `RefreshPolicy.Periodic(15.minutes)`, добавить `READ_CALENDAR` в манифест,
  зарегистрировать в `DashboardViewModel`.
- Оболочка сама рисует рамку/заголовок/статус/бейдж — модуль отдаёт только тело
  `Content()` и обновляет `state`/`refresh()`.
- Не трогать замороженные пакеты `dashboard/*`, `core/time/*`, `settings/*`.
- Константы устройства — из `core.DeviceProfile` (936×1248 dp, 240 dpi, grayscale).
- Сборка/проверки — `./scripts/check.sh` (JDK 17).

## Следующая задача

`T03` — Модуль Google Calendar. **Разблокирована** (как и T04, T05): shell,
замороженные contracts, registry, coordinator, тема, настройки и правила file
ownership переданы; сборка и запуск на устройстве подтверждены.
