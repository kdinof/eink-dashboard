# T03 — Модуль Google Calendar

## Статус

completed

(Весь код и тесты зелёные, сборка debug+release проходит, и **физическая проверка
на подключённом Meebook M103 выполнена**: приложение читает системный Calendar
Provider, число календарей/событий и границы Today / Today+Tomorrow / Week
совпадают с провайдером, all-day и полуночные события отображаются корректно в
зоне устройства (UTC+5), permission-denied показывает корректное состояние без
краха. Числовые/обезличенные результаты — в разделе «Проверки → Физическая
верификация на устройстве». Приватные данные — названия событий, аккаунт, serial
— в отчёт не попадают.)

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
- **Физическое сравнение событий с системным календарём — passed** (на подключённом
  Meebook M103; см. «Физическая верификация на устройстве» ниже).

### Физическая верификация на устройстве (Meebook M103, redacted/numeric only)

Выполнена на реальном устройстве `model=M103 product=rk3566_eink` (Android 11 / SDK 30).
Приватные данные — названия событий, имя/адрес аккаунта, serial — **не** выводились
и не сохранялись; используются только счётчики и обезличенные метки.

- **Установка/пермишен.** Текущий `app-debug.apk` (HEAD `c7fc188`) установлен;
  до переустановки на устройстве стоял устаревший APK **без** `READ_CALENDAR`, что
  подтвердило: разрешение объявляется именно этой сборкой. После установки declared
  permission = только `android.permission.READ_CALENDAR`; `WRITE_CALENDAR` отсутствует.
- **Часовой пояс.** Устройство: `persist.sys.timezone = Etc/GMT-5` — фиксированный
  сдвиг **UTC+5**, эквивалент `Asia/Tashkent` (у Ташкента нет DST), дата устройства
  `Fri, 17 Jul 2026`. Логика `DayRange`/`AgendaBuilder` использует зону устройства,
  поэтому границы дней считаются в +05.
- **Источник истины и сравнение.** Для контролируемой числовой сверки создан
  временный **локальный** (`ACCOUNT_TYPE=LOCAL`, не синхронизируется в Google) календарь
  с 6 известными событиями (timed × сегодня, all-day сегодня, timed завтра, timed на
  неделе, событие 23:30→00:30 через полночь). Провайдер: `calendars=1`, `events=6`.
  Запрос `CalendarContract.Instances` с полуночными границами в +05 дал:
  **Today = 4, Today+Tomorrow = 5, Week(7d) = 6** — ровно то, что рендерит блок
  Calendar в приложении (Today-режим показал 4 записи: all-day первой, затем timed
  09:00 / 10:30 / 23:30, отсортированные по времени, grayscale-маркеры без цвета).
  Числа блока = числа провайдера для всех трёх окон.
- **All-day и полночь.** All-day-событие показано под датой `17 Jul` (а не «уехало»
  на 16-е), что подтверждает UTC-чтение all-day на реальном железе; событие через
  полночь (23:30→00:30) отнесено к дню начала.
- **Permission-denied.** После `pm revoke READ_CALENDAR` и холодного старта приложение
  **не падает** (процесс жив, `MainActivity` resumed, в logcat нет `FATAL`/
  `AndroidRuntime`/`SecurityException` от приложения), блок Calendar показывает
  «Calendar access needed — grant it in Settings» с маркером `!`; остальные модули не
  затронуты. Разрешение затем восстановлено (`granted=true`).
- **Очистка.** Временный локальный календарь и его события удалены; после очистки
  провайдер: `calendars=0`, `events=0` — устройство возвращено в исходное состояние
  (личный Google-аккаунт на устройстве календарей не синхронизирует, поэтому реальных
  событий для сверки не было — отсюда контролируемый локальный набор).

## Известные ограничения

- Физическая сверка с системным календарём **выполнена** на M103 (см. «Физическая
  верификация на устройстве»). Сверка велась контролируемым локальным набором из 6
  событий: на устройстве нет синхронизированного Google-календаря с реальными
  событиями, поэтому «живой» Google-аккаунт как источник не проверялся — числовой
  паритет провайдер↔модуль подтверждён на локальном календаре с идентичным
  `CalendarContract.Instances`-путём.
- Instrumented (Compose UI) тесты не запускались; логика вынесена в чистые слои и
  покрыта JVM-тестами. `CalendarContent`/`CalendarSettingsSection` проверены
  визуально на устройстве (Today-рендер и permission-denied — скриншоты сняты).
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
