# T00 — Аудит Meebook через ADB

## Статус

completed

Физическое устройство подключено, авторизовано и опрошено read-only командами
ADB. Все обязательные характеристики (model, device, Android SDK, ABI,
разрешение, DPI, e-ink возможности, состояние Google sync) подтверждены
командами и перенесены в `DEVICE_AUDIT.md`. Предположений вместо данных нет.

## Краткий результат

Meebook **M103** (OEM Haoqing, SoC **Rockchip RK3566**, `rk356x`), **Android 11 /
API 30**, ABI **arm64-v8a** с 64-битным userspace (`zygote64_32`), экран
**1404×1872 px @ 240 dpi**, панель PVI. Обнаружен реальный vendor e-ink API:
системный сервис `android.os.IEinkManager` (service check: found), демон
`haoqingdrawserver` и кастомные методы Haoqing на `android.app.Activity`
(`autoUpdateMode`, `sendRefreshIcon`, `colorBrightness`, `setHaoQingRunApp`).
Google Play Services, Calendar sync adapter, CalendarProvider и один
`com.google` аккаунт присутствуют → путь T03 жизнеспособен; аудит обезличен
(проверка утечек серийника/email/MAC — 0 совпадений).

## Что реализовано

- Запущен `scripts/device_audit.sh` на реальном устройстве (exit 0); собраны и
  обезличены identity/build, ABI, экран, e-ink пакеты/свойства/сервисы, полный
  `getprop`, отфильтрованный `logcat`.
- Дополнительные read-only проверки: `service check eink/window`,
  `dumpsys account` (только count Google-аккаунтов, email редактирован),
  `pm list packages` grep по GMS/GSF/Calendar/deskclock, `dumpsys window` фокус.
- `DEVICE_AUDIT.md` полностью заполнен confirmed-значениями; помечены отложенные
  на T01/T02/T03 пункты (требуют установленного APK или UI).
- Исправлен баг скрипта: `logcat | tr` падал на non-UTF8 байтах
  (`Illegal byte sequence`) → добавлен `LC_ALL=C` и `grep -a`; повторный прогон
  чистый, e-ink трейсы захвачены.

## Изменённые файлы

- `DEVICE_AUDIT.md` — заполнен подтверждёнными характеристиками устройства
- `scripts/device_audit.sh` — фикс кодировки logcat (`LC_ALL=C`, `grep -a`) + расширен фильтр
- `reports/T00_device_audit.md` — этот отчёт (статус blocked → completed)
- `artifacts/device-audit/*` — обезличенные дампы (gitignored, в git не входят)

## Принятые решения

- APK **не устанавливался**: T00 остаётся read-only, установка smoke APK — область
  T01. Устройство подтверждено как авторизованное для `adb install`.
- Vendor e-ink API описан **только по именам** сервисов/методов из `service list`
  и `logcat`; декомпиляция vendor-приложений не выполнялась (ограничение T00).
  Реверс сигнатур и кодов режимов Normal/Regal/A2 передан в T07.
- Несмотря на 64-bit userspace, рекомендация T01 — **pure Kotlin/Java, minSdk 30**,
  native только при явной необходимости под `arm64-v8a`. ABI больше не блокер.
- Google-аккаунт зафиксирован как факт (count=1), без email — приватность.

## Проверки

- `bash -n scripts/device_audit.sh` — passed
- `scripts/device_audit.sh` на реальном устройстве — passed (exit 0)
- повторный запуск (идемпотентность) — passed (тот же результат, без tr-ошибки)
- проверка утечек `grep -rEi 'серийник|email|@gmail|@icloud'` в `artifacts/` — passed (0 совпадений)
- `service check eink` — passed (`Service eink: found`)
- физическая проверка на устройстве — **passed** (устройство M103 опрошено вживую)
- sleep/wake поведение Activity — not_run (нужен APK → T02, явно отложено)
- установка APK — not_run (read-only T00 → T01, явно отложено)

## Известные ограничения

- Диагональ панели аппаратно не измерялась (assumed ~10.3"); разрешение и density
  подтверждены командами.
- Точные сигнатуры `IEinkManager` / Activity-методов и коды режимов A2/Regal
  неизвестны — реверс в T07 (без декомпиляции в T00).
- Фактический статус sync конкретных Google-календарей подтверждается на UI в T03.
- Security patch устройства старый (2021-06-05) — отметить в T08.

## Риски и технический долг

- Vendor e-ink API недокументирован: `core/eink` **обязан** иметь no-op fallback и
  feature-flag; приложение должно работать на стандартном Android-рендеринге (T07).
- Per-app e-ink optimization системы может влиять на стороннее APK — проверить в T07.
- Если Google Calendar sync выключен пользователем, данных не будет — задокументировать в T03.

## Что должен знать следующий агент

- **Build constraints (T01):** `minSdk = 30`, `targetSdk`/compile под API 30;
  ABI `arm64-v8a` (userspace 64-bit); рекомендация — pure Kotlin/Java без native.
- **Layout (T02):** 1404×1872 px, 240 dpi → ~936×1248 dp; grayscale, без анимаций.
- **E-ink (T07):** сервис `android.os.IEinkManager` + Activity `autoUpdateMode`/
  `sendRefreshIcon`/`colorBrightness` существуют — использовать за feature-flag с
  no-op fallback; коды режимов реверсить здесь.
- **Calendar (T03):** GMS + `com.google.android.syncadapters.calendar` +
  `com.android.providers.calendar` + 1 Google-аккаунт присутствуют — интеграция viable.
- Аудит воспроизводим: `./scripts/device_audit.sh`, значения в
  `artifacts/device-audit/summary.txt` (gitignored, не коммитить сырые дампы).

## Следующая задача

`T01` — Основа Android-проекта. **Разблокирована**: build constraints (ABI,
minSdk, экран, e-ink возможности/неизвестные) подтверждены и переданы. Блокер
физического аудита снят.
