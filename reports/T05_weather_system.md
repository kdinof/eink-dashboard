# T05 — Модули погоды, часов и батареи

## Статус

completed_with_followups

(Функционально completed; единственный followup — физическая проверка на устройстве:
Meebook M103 не был подключён в этой сессии, поэтому on-device install/visual/adb-battery
отмечены `not_run`. Вся логика этих проверок покрыта unit-тестами, включая
adb-emulation-эквивалент значений батареи.)

## Краткий результат

Реализованы три модуля поверх замороженного SPI T02: **Weather** (официальный
keyless Open-Meteo `/v1/forecast`, current + day summary + 7-day forecast,
`timezone=auto`, кэш последнего успешного ответа с offline-fallback, fixed/preset/opt-in
device location), **Clock** (общий минутный тикер, без секунд, дата с переходом
полуночи) и **Battery** (чтение sticky `ACTION_BATTERY_CHANGED`, percent + charging).
Open-Meteo API сверен с официальной докой (2026-07): база `https://api.open-meteo.com/v1/forecast`,
все запрашиваемые `current`/`daily` поля валидны, ключ не требуется. Добавлено 45
unit-тестов (parsing на fixture, cache fallback, refresh policy, location resolution,
clock/battery mapping) — все зелёные; `./scripts/check.sh` собирает debug APK и проходит
`testDebug` + `testRelease`.

## Что реализовано (обязательные действия карточки)

| Требование карточки | Где |
| --- | --- |
| сверить официальный Open-Meteo API до реализации | Проверено WebFetch по `open-meteo.com/en/docs`; сводка зафиксирована в KDoc `data/WeatherApi.kt`; запрос покрыт `RetrofitWeatherApiTest` |
| Ташкент как preset | `model/WeatherModel.kt` → `LocationPresets.TASHKENT` (41.2995, 69.2401) |
| current, day summary, 7-day forecast | `data/WeatherDtos.kt` (`ForecastDto.toSnapshot`), `WeatherSnapshot.current` / `.today` / `.forecast` |
| local timezone из ответа/настроек | `toSnapshot` берёт `timezone` из ответа (`timezone=auto`), он перекрывает hint — тест `apiTimezone_winsOverLocationHint` |
| кэш последнего успешного ответа | `data/WeatherCache.kt` (+ `DataStoreWeatherCache`), fallback в `WeatherRepository.load` |
| fixed location settings | `WeatherSettings` (`FIXED`) + `WeatherSettingsStore` + UI редактор lat/lon |
| геолокация opt-in без постоянного GPS | `data/DeviceLocationSource.kt` — только last-known coarse fix, `ACCESS_COARSE_LOCATION`, никаких active-updates |
| BatteryManager/BroadcastReceiver | `modules/battery/data/BatterySource.kt` — sticky `registerReceiver(null, ACTION_BATTERY_CHANGED)` |
| часы на общий минутный ticker | `modules/clock/ClockModule.kt` — `RefreshPolicy.EveryMinute`, свой таймер отсутствует |
| stale/offline/error states | `WeatherModule` маппит `Fresh→Ok` / `Stale→Ok(isStale)` / `Failed→Error` на frozen `ModuleState` |
| не обновлять погоду каждую минуту | `WeatherModule.refreshPolicy = Periodic(30.minutes)` — тест `refreshPolicy_isNotEveryMinute` |

## Проверка официального Open-Meteo API

Сверено с `https://open-meteo.com/en/docs` (2026-07):

- Endpoint: `GET https://api.open-meteo.com/v1/forecast`.
- `current` (валидно): `temperature_2m, apparent_temperature, relative_humidity_2m,
  weather_code, wind_speed_10m, is_day, precipitation`.
- `daily` (валидно): `weather_code, temperature_2m_max, temperature_2m_min, sunrise,
  sunset, precipitation_sum, precipitation_probability_max`.
- `timezone=auto` → API резолвит зону из координат и отдаёт локальные ISO-времена; зона
  эхом возвращается в поле `timezone` (в снапшоте — авторитетна).
- `forecast_days` — целое 0..16 (используем 7). **API-ключ не требуется** (нужен только
  для commercial reserved resources). Запрос без `apikey` — проверено тестом.
- Ответ: top-level `timezone`, `utc_offset_seconds`, объект `current`, объект `daily`
  с параллельными массивами по дням.

Точный запрос, который шлёт клиент, зафиксирован тестом `RetrofitWeatherApiTest`
(MockWebServer, без реальной сети): проверяются lat/lon, `timezone=auto`,
`forecast_days=7`, наличие всех `current`/`daily` полей и отсутствие `apikey`.

## Архитектура модулей

Все три реализуют frozen `DashboardModule` из T02: не заводят свой цикл, обновляются
только когда координатор вызывает `refresh(reason)`, отдают тело в `Content()`, статус —
через `ModuleState`.

- **Weather** (`id="weather"`, `Periodic(30m)`, `hasSettings=true`):
  `refresh` → `WeatherSettingsStore.current()` → `LocationResolver.resolve()` →
  `WeatherRepository.load()` (сеть+кэш на `Dispatchers.IO`) → маппинг на `ModuleState`.
  Слои разделены seam'ами: `WeatherApi` (Retrofit прячется за интерфейсом),
  `WeatherCache`, `DeviceLocationSource` — всё тестируется fake'ами.
- **Clock** (`id="clock"`, `EveryMinute`): чистый `ClockFormat.face(epochMs, zone)` →
  `ClockFace(time, date)`; модуль — тонкая обёртка над ним и `TimeSource`.
- **Battery** (`id="battery"`, `EveryMinute`): `BatterySource.read()` → `BatteryStatus`
  (`compute(level, scale, charging)` — чистая функция percent'а).

`LocationResolver` гарантирует, что блок никогда не остаётся без места: preset работает
без разрешений; FIXED без валидных координат и DEVICE без fix/permission откатываются к
Ташкенту.

## Изменённые файлы

### Новые — Weather
- `modules/weather/WeatherRepository.kt` — оркестрация fetch+cache, `WeatherLoad` (Fresh/Stale/Failed)
- `modules/weather/WeatherModule.kt` — `DashboardModule`, маппинг на `ModuleState`, composition root `create()`
- `modules/weather/WeatherSettingsStore.kt` — DataStore-персистенс `WeatherSettings` + чистый decode
- `modules/weather/data/LocationResolver.kt` — settings → `ResolvedLocation` с preset-fallback
- `modules/weather/ui/WeatherContent.kt` — grayscale тело (current + 7-day)
- `modules/weather/ui/WeatherFormat.kt` — чистое форматирование температур/дней/осадков
- `modules/weather/ui/WeatherSettingsSection.kt` — выбор источника локации, opt-in coarse permission, редактор lat/lon

### Новые — Clock / Battery
- `modules/clock/ClockModule.kt`, `modules/clock/ClockFormat.kt`
- `modules/battery/BatteryModule.kt`, `modules/battery/data/BatterySource.kt`

### Новые — тесты (45)
- `weather/`: `WeatherDtoTest` (8), `RetrofitWeatherApiTest` (4), `WeatherRepositoryTest` (4),
  `WeatherModuleTest` (6), `LocationResolverTest` (6), `WeatherFormatTest` (5), `WeatherCacheTest` (1),
  `Fakes.kt` (FakeWeatherApi, FakeDeviceLocationSource)
- `clock/ClockModuleTest` (5), `battery/BatteryModuleTest` (6)
- `app/src/test/resources/fixtures/open_meteo_tashkent.json` — сохранённый forecast fixture

### Существовавшие файлы weather-модуля (были в дереве до T05, оставлены как есть/использованы)
- `modules/weather/WeatherSettings.kt`, `model/WeatherModel.kt`, `data/WeatherApi.kt`,
  `data/WeatherDtos.kt`, `data/RetrofitWeatherApi.kt`, `data/WeatherCache.kt`,
  `data/DataStoreWeatherCache.kt`, `data/DeviceLocationSource.kt`

### Изменённые общие точки (аддитивно, разрешено правилами file ownership)
- `dashboard/DashboardViewModel.kt` — регистрация `ClockModule` / `WeatherModule` / `BatteryModule`
  (аппенд после Calendar/Todoist, без реордера чужих блоков)
- `AndroidManifest.xml` — добавлен `ACCESS_COARSE_LOCATION` (opt-in, comment о runtime/last-known)

## Принятые решения

- **Refresh weather = `Periodic(30m)`.** Карточка требует «не обновлять погоду каждую
  минуту». Минутный тик гейтится политикой; погода тянется на resume/manual/settings и
  раз в 30 минут в foreground.
- **Battery через sticky broadcast, не live receiver.** `registerReceiver(null, filter)`
  синхронно отдаёт последний Intent — использует BroadcastReceiver-механизм, но не заводит
  фоновый цикл, что сохраняет foreground-only-гарантию T02. Latency изменения заряда ≤1 мин
  (EveryMinute). Компромисс осознан.
- **Location hint vs response timezone.** `ResolvedLocation.timezone` — только hint; API
  всегда спрашивается с `timezone=auto`, и зона из ответа авторитетна в снапшоте.
- **FIXED/DEVICE fallback к Ташкенту.** Блок никогда не остаётся без места; невалидные
  координаты или отсутствие fix/permission молча дают preset (тест).
- **Регистрация — аппенд, без реордера.** Финальную кросс-модульную геометрию сводит T06.

## Проверки

- `./scripts/check.sh` — **passed** (JDK 17; `testDebug` + `testRelease` + `assembleDebug`;
  `app-debug.apk` собран).
- forecast parsing на сохранённом fixture — **passed** (`WeatherDtoTest`, `RetrofitWeatherApiTest`
  на `open_meteo_tashkent.json`).
- offline cache fallback — **passed** (`WeatherRepositoryTest.failureWithCache_returnsStale`,
  `WeatherModuleTest.networkErrorWithCache_marksStale_keepsSnapshot`).
- смена минуты и даты (в т.ч. полночь и смена зоны) — **passed** (`ClockModuleTest`).
- заряд/разряд + adb-emulation-эквивалент значений — **passed** (`BatteryModuleTest`:
  level/scale mapping как из `dumpsys battery set`, charge/discharge на последовательных тиках).
- отсутствие location permission не ломает preset Ташкента — **passed**
  (`LocationResolverTest.device_withoutFixOrPermission_fallsBackToPreset`,
  `WeatherModuleTest.presetMode_fetchesTashkent_evenWithNoDeviceFix`).
- физический on-device install / визуальная проверка / реальный `adb shell dumpsys battery set`
  — **not_run**: Meebook M103 не подключён в этой сессии (`adb devices` пуст). Точка
  безопасного продолжения — прогнать на устройстве в T06/T07, где панель уже задействована.
- секреты/локальные пути в новых файлах — **проверено**: Open-Meteo keyless, grep по
  `/Users/`, `apikey`, `Bearer` — чисто.

## Известные ограничения

- On-device визуальная проверка недоступна без устройства (и, как отметил T02, `adb screencap`
  на этой e-ink панели отдаёт чёрный кадр). Логика полностью покрыта unit-тестами.
- Device-location использует только last-known coarse fix; при пустом кэше провайдера
  (реально свежая установка без других гео-потребителей) DEVICE до первого системного
  fix'а откатывается к Ташкенту — это осознанно (никакого активного GPS).
- Погодные строки в UI (feels/hum/wind) — компактные; финальную адаптацию плотности под
  портрет/ландшафт и e-ink-типографику сводит T06/T07.

## Риски и технический долг

- `WeatherModule.create()` строит Retrofit-клиент в composition root (без DI, по ADR-0001) —
  консистентно с Calendar/Todoist; при росте графа вынести в `AppContainer`.
- Кэш хранит единственный снапшот (dashboard'у нужно «сейчас», не история) — при желании
  показывать «updated X ago» на конкретный город учитывать, что смена локации перезаписывает кэш.

## Что должен знать следующий агент (T06)

- Три новых модуля зарегистрированы в `DashboardViewModel` (аппенд после Todoist):
  порядок блоков сейчас `calendar, todoist, clock, weather, battery` (+demo в debug).
  Реордер/финальную геометрию свести здесь.
- Weather требует `INTERNET` (уже был) и опционально `ACCESS_COARSE_LOCATION` (добавлен,
  runtime, opt-in из Weather settings). Никаких секретов — Open-Meteo keyless.
- Seam'ы для интеграционных тестов: `WeatherApi`, `WeatherCache`, `DeviceLocationSource`,
  `BatterySource`, `TimeSource` — все с fake'ами в `app/src/test`.
- Замороженные пакеты T02 (`dashboard/*`, `core/time/*`, `settings/*`) не менялись, кроме
  одной строки регистрации в `DashboardViewModel`.
- Физическую проверку батареи на устройстве гонять так: `adb shell dumpsys battery set level N`,
  `... set status 2` (charging) / `... set status 3` (discharging), затем `adb shell dumpsys battery reset`.

## Следующая задача

`T06` — Интеграция модулей и завершённый UX (разблокируется вместе с T03/T04; все три
feature-волны готовы). Base commit: `e0f92ce`.
