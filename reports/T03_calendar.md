# T03 — Модуль Google Calendar

## Статус

completed_with_followups

(Весь код и тесты готовы и зелёные, сборка debug+release проходит. Единственный
незакрытый пункт — **физическое сравнение событий с системным календарём на
устройстве**: к воркеру сейчас не подключён Meebook M103 (`adb devices` пуст), а
сравнение требует реального Google-аккаунта с событиями и выданного `READ_CALENDAR`.
Логика диапазонов/таймзон/полуночи и путь «нет разрешения» покрыты unit-тестами;
точные adb-шаги для доигрывания на устройстве — ниже.)

## Краткий результат

Реализована read-only интеграция с системным Android Calendar Provider
(`CalendarContract`) как подключаемый `DashboardModule` (`id="calendar"`,
`RefreshPolicy.Periodic(15m)`). Модуль читает список календарей и экземпляры
событий (`CalendarContract.Instances`, повторяющиеся уже развёрнуты провайдером),
поддерживает timed / all-day / recurring события, три режима отображения (Today,
Today+Tomorrow, Week), timezone-safe группировку по дням с корректной обработкой
полуночи и `Asia/Tashkent`, различение календарей без цвета (форма+оттенок маркера),
а также состояния permission-denied / empty / stale. Запрашивается только
`READ_CALENDAR`; `WRITE_CALENDAR` не добавляется. Добавлено 31 новых unit-теста
(всего в проекте 58), `./scripts/check.sh` — passed.

## Что реализовано (обязательные действия карточки)

| Требование карточки | Где |
| --- | --- |
| добавить и запросить только `READ_CALENDAR` | `AndroidManifest.xml` (uses-permission); runtime-запрос — `ui/CalendarSettingsSection.kt` через `rememberLauncherForActivityResult` (без правки `MainActivity`) |
| чтение списка календарей | `data/AndroidCalendarDataSource.listCalendars()` |
| выбор календарей в Settings | `ui/CalendarSettingsSection.kt` (per-calendar toggle), хранение — `CalendarSettingsStore` |
| чтение `CalendarContract.Instances` для диапазона | `data/AndroidCalendarDataSource.queryInstances()` |
| timed, all-day и recurring events | модель `CalendarEvent` (`isAllDay`), recurrence развёрнута провайдером в instances; маппинг — `AgendaBuilder` |
| Today / Today+Tomorrow / Week | `CalendarRangeMode` + `DayRange.of(...)` |
| группировка и сортировка событий | `AgendaBuilder` (по дню; all-day → timed; затем по времени, затем по названию) |
| timezone-safe расчёты | `DayRange` работает в `LocalDate` устройства; all-day читается в UTC, timed — в зоне устройства (`AgendaBuilder.coveredDates`) |
| различение календарей оттенком/формой маркера | `model/CalendarMarker.kt` (`MarkerShape` × `shade`), детерминированное присвоение |
| permission denied / empty / stale/offline states | `CalendarModule.refresh()` → `ModuleState.Error/Empty/Ok(isStale)` |
| не запрашивать `WRITE_CALENDAR` | не добавлен нигде (проверено в собранном APK) |

## Изменённые файлы

### Новые (владение T03 — `modules/calendar/**`)

- `modules/calendar/model/CalendarModel.kt` — `CalendarInfo`, `CalendarEvent`, `CalendarRangeMode`, `DayRange` (range-математика).
- `modules/calendar/model/CalendarMarker.kt` — grayscale-маркеры (форма+оттенок) и детерминированное присвоение.
- `modules/calendar/model/AgendaBuilder.kt` — чистая группировка/сортировка по дням (all-day/timed/multi-day).
- `modules/calendar/data/CalendarDataSource.kt` — интерфейс источника (seam для тестов).
- `modules/calendar/data/AndroidCalendarDataSource.kt` — реальное чтение `CalendarContract`.
- `modules/calendar/CalendarSettings.kt` / `CalendarSettingsStore.kt` — настройки модуля (range + выбор календарей) в собственном DataStore-файле.
- `modules/calendar/CalendarRepository.kt` — оркестрация read → `CalendarAgenda`.
- `modules/calendar/CalendarPermission.kt` — seam разрешения + реальная проверка.
- `modules/calendar/CalendarModule.kt` — `DashboardModule` + state-машина.
- `modules/calendar/ui/CalendarFormat.kt` — чистое форматирование заголовков дней/времени.
- `modules/calendar/ui/CalendarContent.kt` — тело блока (агенда, маркеры).
- `modules/calendar/ui/CalendarSettingsSection.kt` — UI настроек модуля + runtime-запрос разрешения.
- Тесты: `DayRangeTest`, `AgendaBuilderTest`, `CalendarMarkerTest`, `CalendarRepositoryTest`, `CalendarSettingsDecodeTest`, `CalendarModuleTest`, хелпер `FakeCalendarDataSource`.

### Изменённые общие файлы (аддитивно — см. «Принятые решения»)

- `AndroidManifest.xml` — `<uses-permission READ_CALENDAR>` (разрешённый shared-touch #3 из отчёта T02).
- `dashboard/DashboardViewModel.kt` — регистрация `CalendarModule.create(app)` (разрешённый shared-touch #1).
- `dashboard/DashboardModule.kt` — **аддитивный** extension point: `val hasSettings` (default false) + `@Composable fun SettingsContent()` (default no-op).
- `settings/ui/SettingsScreen.kt` — рендер `module.SettingsContent()` для модулей с `hasSettings`.

## Принятые решения

- **Провайдер вместо REST.** Использован `CalendarContract` (план §8.1): переиспользует
  синхронизированный на устройстве Google-аккаунт, без Cloud-проекта/OAuth, работает
  офлайн для уже синхронизированных событий.
- **Расширение SPI под настройки модуля (важно для T04/T05/T06).** Карточка требует
  «выбор календарей в Settings», а замороженный shell (T02) не имел точки для
  per-module настроек. Это ровно тот случай «несовместимости», который правила file
  ownership разрешают закрыть изменением общего contract с описанием в отчёте. Введён
  **минимальный аддитивный** slot: `DashboardModule.hasSettings` (default `false`) +
  `SettingsContent()` (default no-op), а `SettingsScreen` рендерит секцию только для
  модулей с `hasSettings`. Существующие модули (demo) не меняются. **T04 (token/фильтры)
  и T05 (локация)** должны реализовывать `hasSettings=true` + `SettingsContent()` вместо
  собственной правки shell.
- **Runtime-запрос разрешения без правки `MainActivity`.** `READ_CALENDAR` запрашивается
  из `CalendarSettingsSection` через `rememberLauncherForActivityResult` — Activity не
  трогается, разрешение остаётся полностью внутри модуля.
- **All-day timezone.** Провайдер хранит all-day как midnight-UTC; дата читается в UTC
  (иначе в `Asia/Tashkent` (UTC+5) событие «уезжает» на день назад). Timed события —
  в зоне устройства. Реализовано и покрыто тестом `allDayEvent_notShiftedByPositiveOffsetZone`.
- **Расширение окна запроса ±1 день** с последующей точной фильтрацией по датам в
  `AgendaBuilder` — чтобы UTC-датированные all-day строки у краёв окна не терялись.
- **Маркеры без цвета.** Форма (5) — первичный сигнал, оттенок (3) — вторичный: 15
  различимых комбинаций до повтора; присвоение детерминировано в каноническом порядке.
- **Выбор календарей хранится как «снятые» id** (как hiddenModuleIds в shell): новый
  календарь по умолчанию виден.
- **Приватность.** Содержимое календаря нигде не логируется; на ошибке — только общий
  текст. `WRITE_CALENDAR` не добавлен (проверено в собранном APK).

## Проверки

- `./scripts/check.sh` — **passed** (JDK 17 / SDK-34; `testDebugUnitTest` +
  `testReleaseUnitTest` + `assembleDebug`; 58/58 unit-тестов зелёные; `app-debug.apk` собран).
- Тест с двумя календарями — **passed** (`CalendarRepositoryTest.twoCalendars_bothSelected...`,
  `deselectingOneCalendar_...`).
- Тест без разрешения — **passed** (`CalendarModuleTest.permissionDenied_yieldsError_andNeverQueries`).
- `Asia/Tashkent` и переход даты — **passed** (unit) (`DayRangeTest.dateTransition_...`,
  `AgendaBuilderTest` all-day/midnight под Tashkent).
- Recurrence mapping — **passed** (`CalendarRepositoryTest.expandedRecurrence_mapsEachOccurrenceToItsOwnDay`).
- `READ_CALENDAR` есть, `WRITE_CALENDAR` нет — **passed** (`aapt dump permissions app-debug.apk`).
- **Физическое сравнение событий с системным календарём — not_run** (нет подключённого
  устройства к этому воркеру; требует личного аккаунта с событиями — не выполняется
  автоматически без риска логирования приватных данных).

### Как доиграть физическую проверку на M103 (для координатора/пользователя)

```
# устройство подключено, USB-debugging включён, аккаунт с событиями синхронизирован
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.eink.dashboard.debug/com.eink.dashboard.MainActivity
# в приложении: Settings → Calendar → «Grant calendar access» → выбрать календари и Range
# либо выдать разрешение через adb:
adb shell pm grant com.eink.dashboard.debug android.permission.READ_CALENDAR
# сверить блок Calendar с системным Google Calendar (Today / +Tomorrow / Week)
# проверить переход даты около полуночи по Asia/Tashkent
```

## Известные ограничения

- Физическая сверка с системным календарём не выполнена (нет устройства) — см. выше.
- Instrumented (Compose UI) тесты не запускались; логика вынесена в чистые слои и
  покрыта JVM-тестами. `CalendarContent`/`CalendarSettingsSection` — визуальные,
  проверяются на устройстве.
- Timed событие, пересекающее полночь, показывается под днём начала (обычное
  agenda-поведение), не разбивается на два дня. Multi-day **all-day** — разворачивается
  по дням.
- Название/цвет календаря из провайдера не используются как hue (панель grayscale);
  идентичность несёт маркер (форма+оттенок).
- `assembleRelease`/R8 не прогонялись (release signing — T09); `compileRelease*` и
  `testReleaseUnitTest` — зелёные. Внимание T09: добавить keep-правила для
  `CalendarContract`-проекций при включённом R8, если понадобится.

## Риски и технический долг

- Регистрация календаря сделала release-реестр непустым (реальный модуль) — ожидаемо
  для T03; demo-модули по-прежнему только в debug.
- `CalendarModule` держит собственный `CalendarSettingsStore` и триггерит
  `refresh(SETTINGS_CHANGED)` из UI при смене настроек — это не периодический цикл
  (событийный, только по действию пользователя), foreground-only-гарантия shell не
  нарушается.
- Одновременный refresh от координатора и от settings-UI безопасен (`MutableStateFlow`,
  последний записавший побеждает); при желании T06 может добавить mutex.

## Что должен знать следующий агент (T04, T05, T06)

- **Новый extension point для настроек модуля:** реализуйте `hasSettings = true` и
  `@Composable fun SettingsContent(modifier)` в своём `DashboardModule` — shell сам
  отрисует секцию под `title` на экране Settings. Не правьте `SettingsScreen`/`MainActivity`.
- **Runtime-разрешения** можно запрашивать из `SettingsContent` через
  `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`,
  не трогая Activity.
- Свои настройки храните в **собственном** DataStore-файле (пример:
  `CalendarSettingsStore` c `preferencesDataStore(name = "...")` и чистым `Keys.decode`);
  не расширяйте замороженный `settings/DashboardSettings`.
- Регистрация модуля — одна строка `.register(...)` в `DashboardViewModel`; порядок =
  порядок блоков.
- Тестовый паттерн: интерфейс-источник данных + fake + чистая логика (range/группировка)
  → JVM-тесты без устройства/Robolectric; state-машину модуля тестировать через
  fake-permission + fake-source (`runBlocking`, in-memory `PreferenceDataStoreFactory`).
- Общий контракт `DashboardModule`/`ModuleState` менять НЕ пришлось, кроме аддитивного
  settings-slot; `core/time/*`, `dashboard/theme/*`, layout/coordinator — не тронуты.

## Следующая задача

`T06` — Integration & UX (после того как T04 и T05 также подключат свои модули).
T04 и T05 остаются разблокированными и независимыми; учитывайте новый settings-slot.
