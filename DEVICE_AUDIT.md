# DEVICE_AUDIT — Meebook e-ink

Задача: [T00 в `AGENT_EXECUTION_PLAN.md`](AGENT_EXECUTION_PLAN.md).
Источник команд: разделы 2, 10, 19 [`EINK_DASHBOARD_IMPLEMENTATION_PLAN.md`](EINK_DASHBOARD_IMPLEMENTATION_PLAN.md).

## Статус аудита

**COMPLETED — устройство подключено, характеристики подтверждены командами ADB.**

- `adb` установлен и работает — `android-platform-tools 37.0.1`,
  `Android Debug Bridge version 1.0.41` (`/opt/homebrew/bin/adb`);
- физический Meebook **подключён и авторизован** — `adb devices` показывает
  `model:M103 device:M103 product:rk3566_eink` в состоянии `device`;
- `scripts/device_audit.sh` выполнен успешно (exit 0); значения ниже перенесены
  из `artifacts/device-audit/summary.txt` и дополнительных read-only команд.

Проверка утечек: `grep` по серийному номеру / email / MAC в `artifacts/device-audit/`
— **0 совпадений**. Серийник и Google-аккаунт в отчёт не переносятся.

Все строки помечены источником:

- `confirmed` — значение получено командой ADB и вписано из артефактов аудита;
- `assumed` — предположение, требует отдельного подтверждения;
- `pending (Txx)` — сознательно отложено на указанный этап (нужен установленный APK).

> Правило заполнения соблюдено: агент запускает `scripts/device_audit.sh`,
> читает `artifacts/device-audit/summary.txt`, переносит значения. Ничего не выдумано.

## Как воспроизвести

```bash
# 1. platform-tools (один раз) — уже установлено на этом хосте
brew install --cask android-platform-tools     # macOS

# 2. На устройстве: Settings → About → 7× tap Build number → Developer options
#    → включить USB debugging. Подключить кабель, принять "Allow USB debugging".

# 3. Проверить соединение
adb devices          # состояние должно быть 'device', не 'unauthorized'

# 4. Запустить аудит (read-only, с редакцией секретов, идемпотентно)
./scripts/device_audit.sh

# 5. Значения из artifacts/device-audit/summary.txt перенесены в таблицы ниже.
```

## 1. Идентификация и сборка

| Параметр | Источник (`getprop`) | Значение | Статус |
|---|---|---|---|
| Производитель | `ro.product.manufacturer` | Haoqing | confirmed |
| Бренд | `ro.haoqing.brand` | haoqing-meebook | confirmed |
| Модель | `ro.product.model` | **M103** | confirmed |
| Device / codename | `ro.product.device` | M103 | confirmed |
| Product name | `ro.product.name` | rk3566_eink | confirmed |
| Board / platform | `ro.board.platform` | rk356x | confirmed |
| Hardware | `ro.hardware` / `ro.boot.hardware` | rk30board | confirmed |
| Product board | `ro.product.board` | rk30sdk | confirmed |
| Android release | `ro.build.version.release` | 11 | confirmed |
| Android SDK | `ro.build.version.sdk` | **30** | confirmed |
| Security patch | `ro.build.version.security_patch` | 2021-06-05 | confirmed |
| Fingerprint | `ro.build.fingerprint` | Haoqing/rk3566_eink/M103:11/RQ2A.210505.003/661:user/release-keys | confirmed |
| Firmware / display id | `ro.build.display.id` | MEEBOOK-V2.0.0-2025070312 | confirmed |
| RK SDK | `ro.rksdk.version` | ANDROID11_RKR10 | confirmed |
| Vendor SDK | `ro.vendor.sdkversion` | rk356x_ANDROID10.0_MID_V1.0 | confirmed |

Итог: устройство — **Meebook M103** (OEM Haoqing) на SoC **Rockchip RK3566**,
**Android 11 (API 30)**. Ранняя неоднозначность «M8 vs M103» снята: `getprop` = M103.
Security patch 2021-06 — старый; учитывать в T08 (без сетевого backend риск ниже).

## 2. ABI и разрядность (критично для T01)

| Параметр | Источник | Значение | Статус |
|---|---|---|---|
| Основной ABI | `ro.product.cpu.abi` | **arm64-v8a** | confirmed |
| Список ABI | `ro.product.cpu.abilist` | arm64-v8a,armeabi-v7a,armeabi | confirmed |
| 32-bit ABI | `ro.product.cpu.abilist32` | armeabi-v7a,armeabi | confirmed |
| 64-bit ABI | `ro.product.cpu.abilist64` | arm64-v8a | confirmed |
| Zygote | `ro.zygote` | **zygote64_32** | confirmed |

Интерпретация:

- `ro.zygote=zygote64_32` → присутствует **и 64-bit, и 32-bit** userspace.
- Устройство **64-битное**. Native-зависимости допустимы, если публикуются под
  `arm64-v8a` (и опционально `armeabi-v7a`).

**Решение для T01:** несмотря на 64-bit поддержку, план предписывает не
добавлять native SDK без необходимости. Рекомендация T00: **pure Kotlin/Java,
`minSdk = 30`, `targetSdk` совместимый с API 30**; если позже понадобится
native — включать `abiFilters = ["arm64-v8a"]` (при желании + `armeabi-v7a`).
ABI больше не является блокером — он подтверждён.

## 3. Экран

| Параметр | Источник | Значение | Статус |
|---|---|---|---|
| Физическое разрешение | `wm size` | **1404 x 1872** px | confirmed |
| Логическая плотность | `wm density` | **240** dpi (bucket ~hdpi/tvdpi) | confirmed |
| Физический дисплей | `dumpsys display` | см. `artifacts/device-audit/display_dumpsys.txt` | confirmed |
| Диагональ панели | — | не измерялась аппаратно | assumed (~10.3") |
| Тип панели | `persist.haoqing.updatemodel` | PVI (`MEEBOOK-M103L-PVI-RK3566`) | confirmed |

Заметки:

- `1404x1872` — портретная физическая ориентация (соотношение 3:4).
- Плотность **240 dpi**: при вёрстке в `dp` ширина ≈ 936 dp, высота ≈ 1248 dp.
  T02 должен рассчитывать layout от этих значений, а не от пикселей.
- Альбомная ориентация даёт логически 1872 x 1404; физический размер панели
  ориентационно-независим. Подтверждение поворота на реальном UI — в T02.

## 4. E-ink / vendor-компоненты (ключ для T07)

| Область | Как найдено | Значение | Статус |
|---|---|---|---|
| Флаг e-ink устройства | `getprop ro.vendor.eink` | **true** | confirmed |
| Модель панели/обновления | `persist.haoqing.updatemodel` | MEEBOOK-M103L-PVI-RK3566 | confirmed |
| Vendor RK SDK | `ro.vendor.rk_sdk` | 1 | confirmed |
| Системный сервис e-ink | `service list` | **`eink` → `android.os.IEinkManager`** | confirmed |
| `service check eink` | ADB | `Service eink: found` | confirmed |
| Draw/refresh демон | `getprop init.svc.haoqingdrawserver` | running | confirmed |
| RK display mgmt сервис | `service list` | `drm_device_management → android.os.IRkDisplayDeviceManagementService` | confirmed |
| Color display сервис | `service list` | `color_display → IColorDisplayManager` | confirmed |

**Framework-level e-ink API (из logcat, подтверждено трейсами системного процесса):**
кастомные методы Haoqing, вкомпилированные в `android.app.Activity`:

| Метод / API | Назначение (по имени) |
|---|---|
| `Activity.autoUpdateMode()` | управление режимом обновления e-ink (Normal/Regal/A2) |
| `Activity.sendRefreshIcon()` | запрос перерисовки / полного refresh |
| `Activity.colorBrightness()` | яркость/контраст фронтальной подсветки |
| `Activity.setHaoQingRunApp()` | регистрация приложения в per-app оптимизации |
| `Activity.haoqingCustomOther()` | прочие кастомные вызовы vendor |
| `com.haoqing.api.util.FunctionUtil` | утилиты (напр. `stopSplitScreen`) — vendor SDK |

Вывод для **T07**: на устройстве есть **реальный vendor e-ink API** двух видов —
(1) системный binder-сервис `android.os.IEinkManager`; (2) кастомные методы на
`Activity` через Haoqing framework. Это НЕ подтверждает публичный/стабильный
контракт: методы недокументированы, доступны, вероятно, только через reflection
или проприетарный Haoqing SDK. Поэтому правило плана сохраняется: **любой vendor
вызов — только за feature-flag с обязательным no-op fallback**; приложение обязано
работать при полном отсутствии этих API (стандартный Android рендеринг).

Режимы Normal/Regal/A2: управляются через `autoUpdateMode`; точные коды режимов
и per-app optimization реверсятся в T07 (в T00 не декомпилируем vendor-приложения).

## 5. Поведение lifecycle (sleep/wake, ориентация)

| Проверка | Метод без готового APK | Результат | Статус |
|---|---|---|---|
| Текущий фокус Activity | `dumpsys window` | читается (`mCurrentFocus`/`mFocusedApp`) | confirmed |
| Тайм-аут экрана | `ro.rk.screenoff_time` | 120000 ms (120 c) | confirmed |
| Возврат в Activity после power | наблюдение | требует установленного APK | pending (T02) |
| Немедленный refresh в `onResume` | реальный APK | — | pending (T02) |
| Поворот portrait/landscape в UI | реальный APK | — | pending (T02) |

Полная проверка sleep/wake поведения Activity требует установленного APK и
относится к T02. На T00 зафиксировано только доступное без APK.

## 6. Установка APK и Google Calendar sync

| Проверка | Метод | Результат | Статус |
|---|---|---|---|
| Устройство авторизовано для `adb install` | `adb devices` state=device | да | confirmed |
| Установка debug APK без root | `adb install` | не выполнялась (T00 read-only, no-install) | pending (T01) |
| Google Play Services / GSF | `pm list packages` | `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending` — присутствуют | confirmed |
| Google Calendar sync adapter | `pm list packages` | `com.google.android.syncadapters.calendar` — присутствует | confirmed |
| Android CalendarProvider | `pm list packages` | `com.android.providers.calendar` — присутствует | confirmed |
| Часы (для модуля clock) | `pm list packages` | `com.android.deskclock` — присутствует | confirmed |
| Google-аккаунт добавлен | `dumpsys account` (count, email редактирован) | **1** аккаунт `type=com.google` | confirmed |
| Calendar sync включён | ручная проверка в системе | не проверялось на уровне UI | pending (T03) |

> Личные данные аккаунта в отчёт не перенесены: зафиксирован только факт наличия
> одного `com.google` аккаунта, без адреса почты.

Вывод: путь **T03 (Google Calendar через CalendarProvider)** технически
жизнеспособен — sync adapter, provider и аккаунт присутствуют. Фактический
статус синхронизации конкретных календарей подтверждается в T03 на UI.

## 7. Ограничения аудита (соблюдены)

- без root;
- без декомпиляции пользовательских приложений (vendor e-ink API описан только
  по именам сервисов/методов из logcat, не по дизассемблированию);
- без установки APK и без изменения системных настроек;
- Developer options / USB debugging — единственное согласованное изменение;
- вывод обезличен: серийный номер, аккаунты, email, MAC/ID редактируются скриптом;
- проверка утечек в артефактах — 0 совпадений.

## 8. Что разблокирует T01 (build constraints — подтверждено)

1. **Model/device:** Meebook M103 (Haoqing), SoC Rockchip RK3566, `ro.board.platform=rk356x`.
2. **Android:** 11 / **API 30** → `minSdk = 30`.
3. **ABI:** `arm64-v8a` (primary), userspace **64-bit** (`zygote64_32`). Native
   допустим под `arm64-v8a`; рекомендация — pure Kotlin/Java без native.
4. **Экран:** 1404×1872 px, density 240 dpi (~936×1248 dp) — базовые раскладки T02.
5. **E-ink возможности:** сервис `android.os.IEinkManager` + Activity-методы
   (`autoUpdateMode` и др.) существуют → стратегия `core/eink` с feature-flag и
   обязательным no-op fallback (T07).
6. **Календарь:** GMS + sync adapter + CalendarProvider + 1 Google-аккаунт → T03 viable.

## 9. Список e-ink возможностей и неизвестных (передача в T07)

**Известно (confirmed):**
- `ro.vendor.eink=true`, панель PVI, RK3566.
- Системный сервис `eink` (`android.os.IEinkManager`) — присутствует.
- Демон `haoqingdrawserver` — работает.
- Framework Activity API: `autoUpdateMode`, `sendRefreshIcon`, `colorBrightness`,
  `setHaoQingRunApp`, `haoqingCustomOther`; vendor util `com.haoqing.api.util.FunctionUtil`.

**Неизвестно (реверс в T07, не в T00):**
- Точная сигнатура/параметры `IEinkManager` и Activity-методов.
- Коды и семантика режимов Normal / Regal / A2.
- Наличие публичного Haoqing SDK (jar) vs только reflection.
- Поведение partial vs full refresh, интервал ghosting-накопления.
- Наличие системной per-app e-ink optimization и её влияние на стороннее APK.
