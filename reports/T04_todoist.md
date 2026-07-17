# T04 — Модуль Todoist

## Статус

completed_with_followups

(Весь код и тесты готовы и зелёные, `./scripts/check.sh` проходит: 103 unit-теста,
debug APK собран, Room-схема v1 экспортирована. Единственные незакрытые пункты —
**физические проверки, требующие реального personal token и подключённого Meebook
M103**: к воркеру устройство не подключено, а вводить реальный секрет в
автоматическом прогоне нельзя по security-требованиям карточки. Точные локальные
шаги для доигрывания — в конце отчёта.)

## Краткий результат

Реализован подключаемый `TodoistModule` (`id="todoist"`, `RefreshPolicy.Periodic(5m)`,
`hasSettings=true`) поверх официального **Todoist API v1** (`https://api.todoist.com/api/v1`,
Bearer-аутентификация) — версия сверена с официальной документацией перед
реализацией (unified v1, бывшие REST v2 + Sync v9). Personal token вводится один раз,
проверяется тестовым запросом и хранится **зашифрованным через Android Keystore**
(AES-256-GCM, ключ неэкспортируемый); token нигде не логируется, не попадает в Room и
DataStore и не вставляется в тексты ошибок. Реализованы Today / Upcoming 7 days с
группировкой по датам, иерархией подзадач, project/labels/priority/due, optimistic
completion, durable pending-queue с retry (переживает рестарт), корректная обработка
recurring-задач и полный маппинг сетевых ошибок 401/403/429/5xx/offline. Добавлено 45
новых unit-тестов (всего 103), в т.ч. token redaction, фильтры, optimistic state и
retry на fake API/cache и реальный клиент на MockWebServer без токена.

## Что реализовано (обязательные действия карточки)

| Требование карточки | Где |
| --- | --- |
| проверить актуальную официальную версию API | v1 сверена по <https://developer.todoist.com/api/v1/>; зафиксировано в `docs/adr/0002-todoist-auth.md` |
| ввод и проверка personal token | `ui/TodoistSettingsSection.kt` (masked ввод) → `TodoistModule.saveAndVerifyToken()` → `TodoistRepository.verifyToken()` (cheap request) |
| хранение token через Android Keystore | `security/KeystoreTokenStore.kt` (AES-256-GCM, `AndroidKeyStore`, неэкспортируемый ключ; на диске только ciphertext) |
| запрет token в логах и Room | нет `HttpLoggingInterceptor`; token только в per-request interceptor; Room/DataStore без token-колонок; `security/TokenStore.kt` `TokenRedaction` |
| Today и Upcoming 7 days | `model/TodoistFilters.kt` (`overdue \| today` / `7 days`), `model/TodoistView` |
| project, labels, priority, due date/time, subtask hierarchy | `model/TaskTreeBuilder.kt` (иерархия+группировка), `ui/TodoistContent.kt` + `ui/TodoistFormat.kt` (priority-глифы, время, project·@labels) |
| optimistic completion | `TodoistRepository.complete()` (мгновенно `locallyCompleted` + очередь), `TodoistModule.complete()` (мгновенный ре-рендер из кэша) |
| Room cache | `data/room/**` (`CachedTaskEntity`, `CachedProjectEntity`, `PendingOpEntity`, `TodoistDao`, `TodoistDatabase`, `RoomTaskCache`), схема v1 в `app/schemas/` |
| durable pending operation + retry | `PendingOpEntity` в Room, `TodoistRepository.drainPending()` (retry на каждом refresh, стоп на первой retryable-ошибке, отказ после `MAX_ATTEMPTS`) |
| recurring tasks | close на сервере переносит задачу; следующий fresh-load показывает новую дату — тест `recurringTask_reappearsWithAdvancedDate_afterCompletion` |
| 401/403/429/5xx и отсутствие сети | `data/TodoistApi.kt` `TodoistError` + маппинг в `RetrofitTodoistApi` (Retry-After для 429) |
| ручной refresh | уже в shell (`DashboardViewModel.refreshNow()` → `RefreshReason.MANUAL`) — модуль реагирует политикой |

## Изменённые файлы

### Новые (владение T04 — `modules/todoist/**`)

- `modules/todoist/model/TodoistModel.kt` — домен: `TodoistTask`, `TodoistDue`, `TodoistProject`, `TodoistView`, `TaskNode`, `TodoistDay`, `TodoistBoard`.
- `modules/todoist/model/TodoistFilters.kt` — view → filter-query (pure).
- `modules/todoist/model/TaskTreeBuilder.kt` — иерархия подзадач + группировка по датам + сортировка (pure).
- `modules/todoist/data/TodoistApi.kt` — seam API + `TaskPage`/`ProjectPage` + `TodoistError`.
- `modules/todoist/data/TodoistDtos.kt` — wire-DTO (kotlinx.serialization) + толерантный маппинг due (date-only / floating / offset).
- `modules/todoist/data/RetrofitTodoistApi.kt` — реальный клиент, per-request Bearer, маппинг ошибок, без logging-интерсептора.
- `modules/todoist/data/TaskCache.kt` — seam кэша + `PendingOp`/`PendingOpType`.
- `modules/todoist/data/room/TodoistEntities.kt`, `TodoistDao.kt`, `TodoistDatabase.kt`, `RoomTaskCache.kt` — Room-кэш (без token).
- `modules/todoist/security/TokenStore.kt` — seam хранилища + `TokenRedaction`.
- `modules/todoist/security/KeystoreTokenStore.kt` — реальная Keystore-реализация.
- `modules/todoist/TodoistSettings.kt` / `TodoistSettingsStore.kt` — настройка view (без token).
- `modules/todoist/TodoistRepository.kt` — оркестрация network + cache + queue; `TodoistLoad`/`CompleteResult`.
- `modules/todoist/TodoistModule.kt` — `DashboardModule` + state-машина + токен-гейтинг.
- `modules/todoist/ui/TodoistContent.kt`, `TodoistFormat.kt`, `TodoistSettingsSection.kt` — тело блока и настройки.
- Тесты: `TokenRedactionTest`, `TodoistFiltersTest`, `TaskTreeBuilderTest`, `TodoistDtoTest`, `TodoistRepositoryTest`, `PendingOpRetryTest`, `TodoistModuleTest`, `TodoistSettingsDecodeTest`, `RetrofitTodoistApiTest`; фейки `FakeTodoistApi`, `FakeTaskCache`, `FakeTokenStore`, `Fixtures`.
- `app/schemas/…/1.json` — экспортированная Room-схема v1.

### Изменённые общие файлы (аддитивно — shared-touch из отчёта T02)

- `AndroidManifest.xml` — `<uses-permission INTERNET>` (для Todoist API).
- `dashboard/DashboardViewModel.kt` — регистрация `TodoistModule.create(app)` (одна строка).
- `app/build.gradle.kts` — `room.schemaLocation` (экспорт схемы).
- `app/proguard-rules.pro` — keep-правила DTO/entities/service для R8 (T09).
- `docs/adr/0002-todoist-auth.md` — новый ADR.

Замороженные пакеты T02 (`dashboard/*` кроме одной строки регистрации, `core/time/*`,
`settings/*`) и модуль Calendar (T03) **не менялись**. Использован settings-slot из
T03 (`hasSettings=true` + `SettingsContent`) — shell не трогался.

## Принятые решения

- **API v1 (unified).** Перед реализацией сверено с официальной докой: Todoist свёл
  REST v2 + Sync v9 в один v1 (`/api/v1`, Bearer). Клиент изолирован за `TodoistApi`,
  так что смена версии/эндпоинта — точечная правка в одном файле. Детали — ADR-0002.
- **Keystore напрямую, без `androidx.security:security-crypto`.** Библиотека
  фактически deprecated; ~30 строк стандартного Keystore дают неэкспортируемый
  AES-GCM-ключ и один аудируемый файл, не раздувая закреплённый набор зависимостей.
  Ключ не требует user-auth (устройство — неаттендед kiosk), но hardware-backed.
- **Token только транзиентно.** Нет logging-интерсептора; заголовок `Bearer`
  добавляется per-request из `TokenStore`; ошибки — фиксированные generic-строки;
  Room/DataStore без секрета. `TokenRedaction` — защитный последний рубеж.
- **Optimistic + durable queue.** Точный 8-шаговый флоу из §8.2: мгновенное скрытие,
  durable `PendingOp` в Room, retry на каждом refresh, откат при terminal-ошибке.
  Рекуррентные задачи не требуют спец-логики (сервер сам переносит дату).
- **Каденция 5 мин** (`Periodic(5.minutes)`) в пределах «2–5 мин + вручную» из плана;
  свой цикл модуль не заводит — foreground-only-гарантия shell сохранена.
- **Тестируемость без устройства.** Все слои за seam-интерфейсами (`TodoistApi`,
  `TaskCache`, `TokenStore`) → fake + чистая логика на JVM; реальный клиент — на
  MockWebServer без токена. Robolectric не используется (как в T02/T03).

## Проверки

- `./scripts/check.sh` — **passed** (JDK 17 / SDK-34; `testDebugUnitTest` +
  `testReleaseUnitTest` + `assembleDebug`; **103/103** unit-тестов зелёные; `app-debug.apk` собран).
- token redaction — **passed** (`TokenRedactionTest`: маскирование всех вхождений,
  generic-сообщения ошибок без секрета).
- filters (Today/Upcoming) — **passed** (`TodoistFiltersTest`).
- optimistic state / rollback / offline — **passed** (`TodoistRepositoryTest`,
  `TodoistModuleTest.complete_hidesTaskFromBoardImmediately`).
- durable retry + exhaustion + stop-on-error — **passed** (`PendingOpRetryTest`).
- recurring task после completion — **passed** (`PendingOpRetryTest.recurringTask_reappears…`).
- mock/web-server tests без реального token — **passed** (`RetrofitTodoistApiTest`:
  200-парсинг, Bearer-заголовок из стора, 401/403/429+Retry-After/5xx, close 204/403).
- subtask hierarchy / группировка / приоритеты — **passed** (`TaskTreeBuilderTest`).
- **Физический smoke test с реальным token на M103 — not_run** (нет устройства и
  реального секрета у воркера; выполняется только локально пользователем — см. ниже).
- **Offline completion → retry на реальной сети — not_run** (логика покрыта unit-тестами
  `PendingOpRetryTest`; на реальном API проверяется локально).

### Как доиграть физические проверки на M103 (для координатора/пользователя, локально)

```
# устройство подключено, USB-debugging включён
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.eink.dashboard.debug/com.eink.dashboard.MainActivity
# в приложении: Settings → Todoist → вставить personal token → «Save & verify»
#   (token берётся из Todoist: Settings → Integrations → Developer → API token)
# проверить:
#  1) появление задач Today / переключение на Upcoming 7d;
#  2) завершение обычной задачи (тап по чекбоксу → исчезает сразу);
#  3) завершение повторяющейся задачи → на следующем refresh появляется с новой датой;
#  4) offline: включить airplane mode, завершить задачу (остаётся скрытой),
#     выключить airplane mode, дождаться refresh → задача уходит на сервере.
# ВАЖНО: token хранится Keystore-шифрованно; в артефакты/логи он не пишется.
```

## Известные ограничения

- Физический smoke с реальным token и M103 не выполнен (нет устройства/секрета) — см. выше.
- `KeystoreTokenStore` (Android Keystore crypto) не покрыт JVM-тестом: Robolectric не
  эмулирует Keystore-крипто надёжно, поэтому это тонкий Android-glue, проверяемый на
  устройстве; вся логика вокруг него — за `TokenStore`-seam и покрыта fake-тестами.
- `RoomTaskCache` — тонкий glue (маппинг entity↔domain), тестируется на устройстве/через
  сборку Room; поведение кэша (optimistic/queue/retry) покрыто через `FakeTaskCache`.
- Instrumented (Compose UI) тесты не запускались; тело/настройки — визуальные, на устройстве.
- `assembleRelease`/R8 не прогонялись (release signing — T09); keep-правила для DTO/Room/
  service добавлены заранее в `proguard-rules.pro`. `compileRelease*`/`testReleaseUnitTest` — зелёные.
- Точный wire-envelope v1 (`results`/`next_cursor`, `tasks/filter`) изолирован в DTO/сервисе —
  при расхождении с сервером правится в одном месте; тесты гоняют собственный JSON на MockWebServer.

## Риски и технический долг

- Todoist personal token — bearer-секрет; при публичной версии заменить на OAuth
  (план §17, ADR-0002 Decision 3). Seam `TokenStore` + per-request interceptor это
  локализуют.
- Keystore-ключ может инвалидироваться при смене lock-screen; `load()` тогда чистит blob
  и заставляет ввести token заново (не крэшит) — приемлемо для kiosk.
- Room-схема v1 без миграций; любое изменение сущностей обязано добавить `Migration`
  (схема закоммичена в `app/schemas/`).
- Регистрация Todoist сделала release-реестр богаче реальными модулями (ожидаемо);
  demo-модули по-прежнему только в debug.

## Что должен знать следующий агент (T06)

- **Токен-гейтинг как у permission в Calendar**: без токена модуль не ходит в сеть и
  показывает `ModuleState.Error` с призывом ввести токен в Settings.
- **Optimistic completion** уже мгновенно скрывает задачу и переживает рестарт через
  durable `PendingOp` в Room; T06 не нужно дублировать очередь.
- **Никаких секретов в логах/Room/ошибках** — при интеграции diagnostics не выводить
  содержимое задач/токен; ошибки уже generic.
- Общий контракт `DashboardModule`/`ModuleState` менять не пришлось; использован
  settings-slot T03. `core/time/*`, `dashboard/theme/*`, layout/coordinator не тронуты.
- Каденция Todoist — `Periodic(5m)`; ручной refresh идёт через shell (`RefreshReason.MANUAL`).

## Следующая задача

`T06` — Integration & UX (после того как T05 также подключит свои модули). T05
остаётся разблокированной и независимой (владеет `modules/weather/**` и системными
модулями). T04 больше не блокирует.
