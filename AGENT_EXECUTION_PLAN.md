# E-Ink Dashboard — план исполнения отдельными агентами

Дата: 17 июля 2026  
Источник требований: [`EINK_DASHBOARD_IMPLEMENTATION_PLAN.md`](EINK_DASHBOARD_IMPLEMENTATION_PLAN.md)

## 1. Назначение документа

Этот документ превращает общий технический план в задачи, которые можно выдавать отдельным агентам. Каждый агент:

1. получает одну ограниченную задачу;
2. читает исходный технический план и отчёты зависимостей;
3. изменяет только разрешённую область проекта;
4. запускает указанные проверки;
5. сохраняет отчёт в `reports/`;
6. передаёт следующему агенту проверяемое состояние репозитория, а не контекст из чата.

Агенты не должны самостоятельно расширять продуктовый scope. Неясности, влияющие на архитектуру, безопасность или UX, фиксируются как blocker/decision request в отчёте.

## 2. Модель исполнения

```text
T00 Device audit
       ↓
T01 Project foundation
       ↓
T02 Dashboard platform
       ↓
 ┌─────┼──────────────┐
 ↓     ↓              ↓
T03   T04            T05
Cal   Todoist        Weather/System
 └─────┼──────────────┘
       ↓
T06 Integration & UX
       ↓
T07 Meebook e-ink adaptation
       ↓
T08 Reliability & security
       ↓
T09 Release candidate
       ↓
T10 Independent final review
```

`T03`, `T04` и `T05` можно выполнять параллельно в отдельных worktree после завершения `T02`. Все остальные задачи последовательны. Если используется один рабочий каталог без отдельных веток, `T03`–`T05` выполнять последовательно.

## 3. Общие правила для всех агентов

### 3.1 Обязательное чтение перед работой

Каждый агент обязан прочитать:

- `EINK_DASHBOARD_IMPLEMENTATION_PLAN.md`;
- эту карточку задачи в `AGENT_EXECUTION_PLAN.md`;
- отчёты всех задач из поля «Зависимости»;
- `git status` и последние изменения;
- `README.md`, `AGENTS.md` и ADR, если они появились к моменту выполнения.

### 3.2 Границы изменений

- Не переписывать чужие завершённые модули без прямой необходимости.
- Не исправлять несвязанные проблемы «заодно».
- Не добавлять backend, Cloudflare, analytics или сторонние SDK без отдельного решения.
- Не логировать calendar data, Todoist token или содержимое приватных задач.
- Не ослаблять тесты ради зелёного результата.
- Не менять публичные контракты модулей без отражения в ADR и отчёте.
- Не копировать код Tesserae; разрешено использовать только архитектурные идеи.

### 3.3 Definition of Done любой задачи

Задача считается завершённой, только если:

- все обязательные deliverables созданы;
- сборка затронутой части проходит;
- тесты из карточки выполнены либо явно отмечены как невозможные с причиной;
- нет новых секретов или локальных путей в git;
- создан отчёт `reports/TXX_<slug>.md`;
- в отчёте перечислены изменённые файлы и известные ограничения;
- следующий агент может начать работу, читая только репозиторий и отчёты.

### 3.4 Запрещённое завершение

Нельзя писать «готово», если:

- код не собирался;
- физическая проверка обязательна, но не выполнялась;
- тесты падают;
- используются заглушки, которые выглядят как рабочая интеграция;
- часть требований молча отложена;
- отчёт отсутствует.

## 4. Формат отчёта агента

Каждая задача создаёт один файл по шаблону:

```markdown
# TXX — название задачи

## Статус
completed | blocked | completed_with_followups

## Краткий результат
2–5 предложений о фактическом результате.

## Что реализовано
- ...

## Изменённые файлы
- `path/to/file` — назначение изменения

## Принятые решения
- решение и причина

## Проверки
- `команда` — passed/failed/not_run
- физическая проверка — результат

## Известные ограничения
- ...

## Риски и технический долг
- ...

## Что должен знать следующий агент
- контракты, точки расширения, важные команды

## Следующая задача
`TYY` или список разблокированных задач.
```

Если задача заблокирована, отчёт дополнительно должен содержать:

- точную причину;
- уже выполненные проверки;
- минимальное действие пользователя/координатора для разблокировки;
- безопасную точку продолжения.

## 5. Карточки задач

## T00 — Аудит Meebook через ADB

**Исполнитель:** отдельный агент диагностики устройства  
**Зависимости:** нет  
**Разблокирует:** `T01`

### Цель

Получить подтверждённые характеристики устройства и определить доступный способ e-ink-управления до выбора окончательных параметров Android-проекта.

### Входные данные

- физическое устройство;
- USB-кабель;
- согласие пользователя включить Developer options и USB debugging;
- разделы 2, 10 и 19 общего плана.

### Обязательные действия

- объяснить пользователю включение Developer options и USB debugging;
- проверить наличие `adb` и при необходимости дать инструкцию установки;
- выполнить базовые команды из общего плана;
- подтвердить model, device, Android SDK, ABI, разрешение и DPI;
- проверить поведение Activity/системы после sleep/wake настолько, насколько это возможно без готового APK;
- найти системные пакеты, свойства и сервисы, связанные с Meebook/Boyue/e-ink/EPD;
- снять logcat при ручной смене режимов Normal/Regal/A2, если режимы доступны;
- проверить, 32- или 64-битная userspace;
- зафиксировать состояние Google Calendar sync и возможность установки APK без записи личных данных.

### Deliverables

- `DEVICE_AUDIT.md`;
- `scripts/device_audit.sh` без destructive-команд;
- обезличенные диагностические выводы в `artifacts/device-audit/` при необходимости;
- `reports/T00_device_audit.md`.

### Обязательные проверки

- скрипт запускается повторно;
- вывод не содержит Google account, Todoist token, серийный номер или другие лишние идентификаторы;
- выводы в `DEVICE_AUDIT.md` подтверждены командами, а предположения помечены.

### Ограничения

- не получать root;
- не декомпилировать пользовательские приложения;
- не менять системные настройки, кроме явно согласованных Developer options;
- не начинать Android-проект до фиксации ABI.

### Передача

`T01` должен получить подтверждённые build constraints и список e-ink-возможностей/неизвестных.

## T01 — Основа Android-проекта и архитектурные решения

**Исполнитель:** отдельный агент Android foundation  
**Зависимости:** `T00`  
**Разблокирует:** `T02`

### Цель

Создать минимальный собираемый Android-проект с воспроизводимым toolchain и зафиксировать архитектурные решения, не реализуя продуктовые интеграции.

### Обязательные действия

- выбрать версии Kotlin, AGP, Compose и зависимостей, совместимые с результатами аудита;
- создать приложение с `minSdk 30`;
- настроить namespace/application id;
- создать пакеты `core`, `dashboard`, `modules`, `settings`, `diagnostics`;
- подключить Coroutines, Room, DataStore, Retrofit/OkHttp и test stack;
- настроить debug/release build types без реальных signing secrets;
- добавить `.gitignore`, Gradle wrapper и базовый README;
- создать ADR по выбору single-activity Compose, модульному registry и отсутствию backend;
- добавить минимальную CI-проверку или локальный `scripts/check.sh`.

### Deliverables

- собираемый Android-проект;
- `README.md` с командами сборки;
- `docs/adr/0001-architecture.md`;
- `scripts/check.sh`;
- `reports/T01_project_foundation.md`.

### Обязательные проверки

- `./gradlew assembleDebug`;
- `./gradlew test`;
- установка smoke APK на Meebook, если устройство доступно;
- подтверждение, что APK содержит ABI, совместимые с устройством, либо не содержит native code.

### Не входит

- реальный Calendar UI;
- Todoist API;
- Open-Meteo;
- vendor e-ink вызовы;
- полноценный Dashboard UI.

### Передача

`T02` получает стабильную структуру проекта, команды проверок и архитектурные контракты.

## T02 — Платформа дашборда и e-ink-safe UI shell

**Исполнитель:** отдельный агент UI/platform  
**Зависимости:** `T01`  
**Разблокирует:** `T03`, `T04`, `T05`

### Цель

Создать общий Dashboard shell, contracts для блоков, настройки и безопасную для e-ink базовую тему, на которую независимо подключатся источники данных.

### Обязательные действия

- реализовать single Activity и immersive fullscreen;
- реализовать `DashboardModule`, `ModuleState`, `RefreshPolicy`, `RefreshReason`;
- создать `DashboardModuleRegistry`;
- реализовать foreground-only refresh coordinator;
- реализовать минутный ticker без секунд;
- остановку циклов в background и немедленный refresh в `onResume`;
- портретный и альбомный шаблоны;
- grayscale design tokens;
- отключение анимаций, ripple и плавных переходов;
- DataStore-настройки ориентации, keep-screen-on и видимости блоков;
- базовые экраны Dashboard, Settings и Diagnostics;
- fake/demo modules для проверки layout, явно отмеченные как demo.

### Deliverables

- рабочий UI shell;
- документированный module contract;
- unit-тесты scheduler/lifecycle logic;
- screenshot/golden baselines для двух ориентаций;
- `reports/T02_dashboard_platform.md`.

### Обязательные проверки

- `./scripts/check.sh`;
- поворот portrait/landscape;
- sleep/wake на устройстве;
- часы обновляются на границе минуты;
- background не выполняет периодические API refresh;
- demo state не попадает в release как реальные пользовательские данные.

### File ownership

Агент владеет общими contracts и shell. Он не должен заранее создавать внутренние реализации Calendar/Todoist/Weather, кроме интерфейсов подключения.

### Передача

Отчёт должен содержать точный пример подключения нового модуля и правила file ownership для `T03`–`T05`.

## T03 — Модуль Google Calendar

**Исполнитель:** отдельный агент Calendar  
**Зависимости:** `T02`  
**Разблокирует:** `T06`

### Цель

Реализовать read-only интеграцию с Android Calendar Provider и три режима отображения.

### Обязательные действия

- добавить и запросить только `READ_CALENDAR`;
- реализовать чтение списка календарей;
- реализовать выбор календарей в Settings;
- читать `CalendarContract.Instances` для диапазона;
- поддержать timed, all-day и recurring events;
- реализовать Today, Today+Tomorrow и Week;
- группировать и сортировать события;
- использовать timezone-safe расчёты;
- различать календари оттенком и формой маркера;
- показать permission denied, empty и stale/offline states;
- не запрашивать `WRITE_CALENDAR`.

### Deliverables

- `modules/calendar/` и минимально необходимые общие изменения;
- fake Calendar data source для unit/UI tests;
- тесты диапазонов, полуночи, all-day и recurrence mapping;
- `reports/T03_calendar.md`.

### Обязательные проверки

- `./scripts/check.sh`;
- тест с двумя календарями;
- тест без разрешения;
- физическое сравнение событий с системным календарём;
- проверка `Asia/Tashkent` и перехода даты.

### File ownership

Основная область — Calendar module. Изменения общих contracts допускаются только при несовместимости и должны быть описаны в отчёте для `T04`, `T05` и `T06`.

## T04 — Модуль Todoist

**Исполнитель:** отдельный агент Todoist  
**Зависимости:** `T02`  
**Разблокирует:** `T06`

### Цель

Реализовать безопасное получение и завершение Todoist-задач с локальным кэшем и устойчивостью к сети.

### Обязательные действия

- перед реализацией проверить актуальную официальную версию Todoist API;
- реализовать ввод и проверку personal token;
- хранить token через Android Keystore-backed решение;
- запретить token в логах и Room;
- реализовать Today и Upcoming 7 days;
- показать project, labels, priority, due date/time и subtask hierarchy;
- реализовать optimistic completion;
- реализовать Room cache;
- реализовать durable pending operation и retry;
- корректно обработать recurring tasks;
- обработать 401/403/429/5xx и отсутствие сети;
- добавить ручной refresh.

### Deliverables

- `modules/todoist/` и необходимые schema/migrations;
- fake API и тестовый repository;
- тесты token redaction, filters, optimistic state и retry;
- `docs/adr/0002-todoist-auth.md`;
- `reports/T04_todoist.md`.

### Обязательные проверки

- `./scripts/check.sh`;
- mock/web-server tests без реального token;
- физический smoke test с token выполняется только локально и не сохраняет секрет в артефактах;
- завершение обычной и повторяющейся тестовой задачи;
- offline completion с последующим retry.

### File ownership

Основная область — Todoist module, security helper и его Room entities. Не менять Dashboard layout.

## T05 — Модули погоды, часов и батареи

**Исполнитель:** отдельный агент Weather/System  
**Зависимости:** `T02`  
**Разблокирует:** `T06`

### Цель

Реализовать Open-Meteo, фиксированное/опциональное местоположение, часы и состояние батареи.

### Обязательные действия

- перед реализацией проверить актуальную официальную Open-Meteo API;
- добавить Ташкент как preset;
- реализовать current, day summary и 7-day forecast;
- использовать local timezone из ответа/настроек;
- кэшировать последний успешный ответ;
- реализовать fixed location settings;
- геолокацию сделать opt-in без постоянного GPS;
- реализовать BatteryManager/BroadcastReceiver;
- подключить часы к общему минутному ticker;
- показать stale/offline/error states;
- не обновлять погоду каждую минуту.

### Deliverables

- `modules/weather/`, `modules/clock/`, `modules/battery/`;
- fake data sources;
- тесты parsing, cache fallback и refresh policy;
- `reports/T05_weather_system.md`.

### Обязательные проверки

- `./scripts/check.sh`;
- forecast parsing на сохранённом fixture;
- offline cache;
- смена минуты и даты;
- заряд/разряд или adb-emulation battery event;
- отсутствие location permission не ломает preset Ташкента.

### File ownership

Не менять Calendar, Todoist и общую Dashboard-геометрию кроме регистрации своих modules.

## T06 — Интеграция модулей и завершённый пользовательский UX

**Исполнитель:** отдельный агент integration  
**Зависимости:** `T03`, `T04`, `T05`  
**Разблокирует:** `T07`

### Цель

Объединить три независимые интеграции в цельный Dashboard, разрешить конфликты и довести основные пользовательские сценарии до сквозного состояния.

### Обязательные действия

- прочитать все три отчёта;
- интегрировать registries и migrations;
- разрешить конфликты общих contracts минимальными изменениями;
- убрать demo modules из пользовательского release flow;
- завершить portrait/landscape layouts;
- реализовать единый last-updated/offline indicator;
- обеспечить доступ к Settings без анимационных overlay;
- проверить сохранение всех настроек;
- реализовать понятные empty/error/permission states;
- добавить ручной refresh всех модулей;
- проверить touch targets и отсутствие случайного завершения задач;
- обновить README пользовательскими шагами настройки.

### Deliverables

- единый working app;
- интеграционные тесты основных сценариев;
- обновлённые screenshots/goldens;
- `reports/T06_integration_ux.md`.

### Обязательные проверки

- clean `./scripts/check.sh`;
- clean install и upgrade install;
- настройка Calendar, Todoist и погоды с нуля;
- portrait/landscape;
- offline launch;
- sleep/wake;
- process recreation;
- никакие секреты/личные данные не входят в fixtures или screenshots.

### Ограничения

Этот агент исправляет интеграционные проблемы, но не выполняет глубокую vendor e-ink reverse engineering — это `T07`.

## T07 — Адаптация под Meebook и ghosting

**Исполнитель:** отдельный агент device/e-ink  
**Зависимости:** `T06`, использует результаты `T00`  
**Разблокирует:** `T08`

### Цель

Настроить реальное приложение под экран Meebook, реализовать безопасный `EInkController` и измерить поведение ghosting/питания.

### Обязательные действия

- реализовать no-op/default Android adapter;
- при наличии подтверждённого API реализовать Meebook adapter;
- не использовать непроверенные reflection/vendor вызовы без feature flag и fallback;
- добавить partial/full refresh abstraction;
- полный refresh после orientation change и `onResume`, если это полезно на устройстве;
- настройка периодического full refresh;
- проверить Normal/Regal/A2 и системную per-app optimization;
- подобрать grayscale palette, strokes и typography;
- провести не менее 60 минут минутных обновлений;
- сравнить keep-screen-on on/off;
- записать субъективные и измеримые результаты ghosting/battery.

### Deliverables

- `core/eink/` adapters;
- `EINK_TEST_REPORT.md`;
- при необходимости фото/screenshots в `artifacts/eink-tests/` без личных данных;
- `docs/adr/0003-eink-refresh.md`;
- `reports/T07_meebook_eink.md`.

### Обязательные проверки

- приложение работает при полном отсутствии vendor API;
- vendor exception не приводит к crash;
- 60-минутный ghosting test;
- portrait/landscape refresh;
- sleep/wake;
- измерение батареи с зафиксированными условиями.

### Передача

`T08` должен получить конкретные рекомендованные defaults, а не набор непроверенных вариантов.

## T08 — Надёжность, приватность и security review

**Исполнитель:** отдельный агент quality/security  
**Зависимости:** `T07`  
**Разблокирует:** `T09`

### Цель

Проверить приложение как продукт: ошибки сети, process death, миграции, секреты, permissions и длительную работу.

### Обязательные действия

- составить traceability matrix требований к тестам;
- проверить permissions и manifest components;
- проверить отсутствие token/личных данных в логах, backups, fixtures и screenshots;
- проверить Room migrations;
- проверить process death и state restoration;
- проверить midnight/timezone cases;
- проверить Calendar permission revoked;
- проверить Todoist 401/403/429/5xx;
- проверить Open-Meteo timeout/bad payload;
- проверить offline queue;
- выполнить длительный foreground run;
- проверить утечки и чрезмерные wakeups;
- исправить только найденные дефекты в рамках требований;
- все существенные исправления описать в отчёте.

### Deliverables

- `TEST_PLAN.md`;
- `SECURITY_REVIEW.md`;
- расширенные automated tests;
- `reports/T08_reliability_security.md`.

### Обязательные проверки

- clean build/test/lint;
- fresh install;
- upgrade from предыдущего debug/release-compatible schema;
- offline сценарии;
- длительный тест;
- ручная проверка logcat на секреты.

### Gate

Если найден риск потери задач, утечки token или повреждения базы, `T09` блокируется до исправления.

## T09 — Release candidate и эксплуатационная документация

**Исполнитель:** отдельный агент release  
**Зависимости:** `T08`  
**Разблокирует:** `T10`

### Цель

Подготовить устанавливаемый release candidate и инструкции, не публикуя его во внешние магазины.

### Обязательные действия

- определить versionName/versionCode;
- настроить release signing через локальные переменные/инструкцию без коммита ключей;
- собрать release APK;
- проверить установку и upgrade;
- написать руководство по установке через ADB;
- написать первичную настройку Calendar/Todoist/Weather;
- документировать backup/export/import, если реализованы;
- создать troubleshooting;
- создать changelog;
- посчитать checksum APK;
- не загружать APK или исходники наружу без отдельного запроса.

### Deliverables

- release candidate APK в gitignored `artifacts/release/`;
- checksum;
- `docs/INSTALLATION.md`;
- `docs/USER_GUIDE.md`;
- `docs/TROUBLESHOOTING.md`;
- `CHANGELOG.md`;
- `reports/T09_release_candidate.md`.

### Обязательные проверки

- clean checkout build по документированной команде;
- APK устанавливается на Meebook;
- release не содержит debug endpoint, demo data или test token;
- upgrade сохраняет настройки и кэш;
- checksum воспроизводимо рассчитан.

## T10 — Независимый финальный review

**Исполнитель:** отдельный агент, не выполнявший `T01`–`T09`  
**Зависимости:** `T09`  
**Разблокирует:** решение пользователя о принятии версии

### Цель

Независимо проверить соответствие исходному плану и определить, можно ли считать версию готовой к ежедневному использованию.

### Обязательные действия

- не полагаться на заявления предыдущих агентов без проверки;
- сопоставить все требования с реализацией;
- прочитать все отчёты;
- запустить clean build/test;
- проверить release APK;
- проверить наиболее рискованные сценарии на устройстве;
- провести code review security-critical частей;
- отметить P0/P1/P2/P3 findings;
- не вносить крупные исправления самостоятельно;
- мелкие очевидные исправления допустимы только если они не меняют архитектуру и полностью проверены.

### Deliverables

- `FINAL_ACCEPTANCE_REPORT.md`;
- `reports/T10_final_review.md`;
- решение: `accepted`, `accepted_with_followups` или `rejected`;
- список follow-up задач, если нужен.

### Критерии принятия

- нет P0/P1 дефектов;
- нет утечки секретов;
- Calendar, Todoist completion, Weather cache и sleep/wake работают на Meebook;
- ghosting контролируется выбранной политикой;
- release воспроизводим;
- документация позволяет переустановить приложение без помощи автора кода.

## 6. Правила параллельной волны T03–T05

Если задачи идут параллельно:

- каждая выполняется в отдельном worktree от одного и того же завершённого commit `T02`;
- Calendar владеет `modules/calendar/`;
- Todoist владеет `modules/todoist/` и своими security/storage helpers;
- Weather/System владеет `modules/weather/`, `modules/clock/`, `modules/battery/`;
- общие contracts после `T02` считаются замороженными;
- необходимость изменить contract оформляется как decision request координатору;
- слияние и разрешение конфликтов принадлежит `T06`, а не feature-агентам;
- каждый feature-agent указывает base commit и итоговый commit в отчёте.

## 7. Оркестрационный протокол

При supervised orchestration координатор должен:

1. создать task с зависимостями;
2. назначить одного агента;
3. передать полную карточку задачи;
4. дождаться completion message;
5. проверить отчёт и тесты;
6. отметить task completed только после проверки;
7. разблокировать зависимые задачи;
8. использовать decision gate при архитектурной неопределённости.

Сообщение о завершении не заменяет файл отчёта. В completion message должны быть только:

- статус;
- краткий итог;
- путь к отчёту;
- изменённые файлы/commit;
- blocker или следующая разблокированная задача.

## 8. Реестр состояния

Координатор поддерживает таблицу ниже. Агенты не меняют чужие строки.

| Task | Status | Agent | Report | Commit/Artifact |
|---|---|---|---|---|
| T00 | pending | — | `reports/T00_device_audit.md` | — |
| T01 | pending | — | `reports/T01_project_foundation.md` | — |
| T02 | pending | — | `reports/T02_dashboard_platform.md` | — |
| T03 | pending | — | `reports/T03_calendar.md` | — |
| T04 | pending | — | `reports/T04_todoist.md` | — |
| T05 | pending | — | `reports/T05_weather_system.md` | — |
| T06 | pending | — | `reports/T06_integration_ux.md` | — |
| T07 | pending | — | `reports/T07_meebook_eink.md` | — |
| T08 | pending | — | `reports/T08_reliability_security.md` | — |
| T09 | pending | — | `reports/T09_release_candidate.md` | — |
| T10 | pending | — | `reports/T10_final_review.md` | — |

## 9. Стартовая команда координатору

Первая исполнимая задача — только `T00`.

Перед её запуском пользователь должен иметь физический Meebook и USB-кабель рядом. Если устройство сейчас недоступно, `T00` не следует подменять предположениями; можно подготовить скрипт аудита, но задача останется `blocked` до получения реальных данных.

