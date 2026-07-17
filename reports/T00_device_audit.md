# T00 — Аудит Meebook через ADB

## Статус

blocked

Причина: физическое устройство не подключено по USB. `adb` установлен с
разрешения координатора и работает; остался единственный блокер — само
устройство. Фактический сбор характеристик не выполнялся и не подменялся
предположениями.

## Краткий результат

С разрешения координатора установлен `android-platform-tools 37.0.1`
(`adb 1.0.41`, `/opt/homebrew/bin/adb`); безопасный аудит повторно запущен —
`adb devices` возвращает пустой список, `scripts/device_audit.sh` корректно
завершается кодом 3 (нет авторизованного устройства). Подготовлены все
deliverables T00: не деструктивный, повторно запускаемый и обезличивающий
секреты скрипт `scripts/device_audit.sh`, документ-каркас `DEVICE_AUDIT.md` с
явными метками confirmed/assumed/pending и каталог `artifacts/device-audit/`.
Задача остаётся `blocked` до подключения Meebook с включённым USB debugging;
после запуска скрипта значения переносятся в `DEVICE_AUDIT.md` и статус
меняется на completed.

## Что реализовано

- `scripts/device_audit.sh` — read-only ADB-аудит:
  - preflight: проверка `adb`, подсчёт авторизованных устройств, инструкции при
    их отсутствии (Developer options → USB debugging → accept prompt);
  - сбор identity/build, ABI/разрядности (`ro.zygote`), экрана (`wm size`,
    `wm density`, `dumpsys display`), e-ink/vendor пакетов и свойств
    (`eink|epd|boyue|likebook|meebook|onyx|rockchip`), системных сервисов,
    полного `getprop`, отфильтрованного `logcat -d`;
  - редакция серийников, email/аккаунтов, MAC, числовых/Android ID перед записью;
  - идемпотентность и явные коды выхода (2 — нет adb, 3 — нет устройства).
- `DEVICE_AUDIT.md` — таблицы по разделам (identity, ABI, экран, e-ink,
  lifecycle, установка APK/Calendar sync) с колонкой статуса
  confirmed/assumed/pending и инструкцией заполнения.
- `artifacts/device-audit/` — `README.md` + `.gitignore` (данные аудита не
  коммитятся, трекается только документация).

## Изменённые файлы

- `scripts/device_audit.sh` — read-only ADB-аудит с редакцией и preflight-инструкциями
- `DEVICE_AUDIT.md` — каркас отчёта устройства (confirmed/assumed/pending)
- `artifacts/device-audit/README.md` — описание артефактов аудита
- `artifacts/device-audit/.gitignore` — исключение данных аудита из git
- `reports/T00_device_audit.md` — этот отчёт

## Принятые решения

- `adb`/platform-tools установлены **только после явного разрешения
  координатора** (msg `unblock-platform-tools`). Первичная попытка brew-cask
  упала на устаревшем broken-симлинке `/opt/homebrew/bin/adb`; после его
  удаления установка прошла успешно. Автоматически, без разрешения, ПО не
  ставилось — это выходило бы за read-only-рамки T00.
- Данные аудита обезличиваются на уровне скрипта (fail-closed редакция) и
  дополнительно исключаются из git, чтобы исключить утечку серийника/аккаунта.
- Проверка sleep/wake поведения Activity и установки APK помечены как pending и
  отнесены к T01/T02 (требуют установленного APK) — на T00 без APK они
  недоступны, что явно зафиксировано.

## Проверки

- `bash -n scripts/device_audit.sh` — passed (синтаксис корректен)
- `scripts/device_audit.sh` без adb — passed (exit 2, печатает инструкцию установки)
- повторный запуск скрипта — passed (идемпотентно, тот же результат)
- прогон с mock-adb (симуляция устройства с секретами) — passed: собран
  summary (model M103, ABI arm64-v8a, zygote64_32, 1404x1872, density 227,
  boyue eink service), проверка утечки `grep` по serial/email/MAC — 0 совпадений
- `adb version` после установки — passed (1.0.41 / platform-tools 37.0.1)
- `scripts/device_audit.sh` с установленным adb, без устройства — passed
  (exit 3, `adb devices` пуст, печатает checklist подключения)
- физическая проверка на устройстве — not_run (устройство не подключено)

## Известные ограничения

- Нет подтверждённых значений устройства: model/device, ABI/разрядность,
  разрешение/DPI, e-ink API — всё pending до подключения.
- Модель в переписке противоречива (M8 vs M103) — требует подтверждения `getprop`.
- logcat-захват режимов Normal/Regal/A2 требует ручного переключения на
  устройстве между `adb logcat -c` и повторным запуском.
- Скрипт протестирован на macOS-хосте и mock-adb, не на реальном Android.

## Риски и технический долг

- Если userspace окажется только 32-bit (`ro.zygote=zygote32`), T01 обязан
  избегать нативных зависимостей — правило уже зафиксировано в `DEVICE_AUDIT.md`.
- Vendor e-ink API может отсутствовать или быть недоступен без root → `core/eink`
  обязан иметь no-op fallback (передаётся в T07).
- Calendar sync зависит от системного аккаунта; при выключенном sync данных не
  будет — задокументировать для пользователя (T03).

## Что должен знать следующий агент

- T01 **не** стартует до фиксации ABI. До подтверждения — `minSdk = 30`, pure
  Kotlin/Java, без native libs.
- Заполнение аудита (`adb` уже установлен): включить USB debugging →
  `adb devices` (состояние `device`) → `./scripts/device_audit.sh` →
  перенести значения из `artifacts/device-audit/summary.txt` в `DEVICE_AUDIT.md`,
  сменив статусы на `confirmed` и обновив статус этого отчёта на `completed`.
- Секреты редактируются скриптом; артефакты аудита gitignored — не коммитить сырые дампы.

## Следующая задача

`T01` — Основа Android-проекта. **Заблокирована** до завершения физического
аудита (подтверждённый ABI/build constraints). `adb` уже готов; разблокирующее
действие: подключить Meebook с USB debugging, запустить
`scripts/device_audit.sh`, затем дозаполнить `DEVICE_AUDIT.md`.

---

### Точка безопасного продолжения (для blocked-статуса)

- **Точная причина:** физический Meebook не подключён по USB (`adb devices`
  пуст). `adb` установлен и работает — это часть блокера снята.
- **Уже выполнено:** окружение проверено; `adb` установлен (1.0.41); скрипт
  написан, синтаксически валиден, идемпотентен, протестирован на mock-adb с
  проверкой редакции и повторно запущен с реальным adb (exit 3); каркас
  `DEVICE_AUDIT.md` и артефакты готовы.
- **Минимальное действие для разблокировки:** (1) включить Developer options +
  USB debugging на Meebook; (2) подключить кабель и принять «Allow USB
  debugging»; (3) `adb devices` должен показать состояние `device`; (4)
  запустить `./scripts/device_audit.sh`; (5) перенести подтверждённые значения
  в `DEVICE_AUDIT.md`.
- **Безопасная точка продолжения:** после заполнения таблиц `confirmed` —
  сменить статус на `completed` и разблокировать `T01`.
