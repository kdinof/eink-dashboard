# DEVICE_AUDIT — Meebook e-ink

Задача: [T00 в `AGENT_EXECUTION_PLAN.md`](AGENT_EXECUTION_PLAN.md).
Источник команд: разделы 2, 10, 19 [`EINK_DASHBOARD_IMPLEMENTATION_PLAN.md`](EINK_DASHBOARD_IMPLEMENTATION_PLAN.md).

## Статус аудита

**BLOCKED — ожидает подключения физического устройства.**

На момент подготовки:

- `adb` **установлен и работает** — `android-platform-tools 37.0.1`,
  `Android Debug Bridge version 1.0.41` (`/opt/homebrew/bin/adb`);
- физический Meebook **не подключён** — `adb devices` возвращает пустой список,
  `scripts/device_audit.sh` завершается кодом 3 (no authorized device);
- поэтому ни одно поле ниже не подтверждено командой устройства.

Остался единственный блокер: подключить Meebook с включённым USB debugging.

Все строки в таблицах помечены источником:

- `confirmed` — значение получено командой ADB и вписано из
  `artifacts/device-audit/summary.txt`;
- `assumed` — значение из переписки/плана, **требует подтверждения**;
- `pending` — ещё не собрано.

> Правило заполнения: агент/пользователь запускает `scripts/device_audit.sh`,
> открывает `artifacts/device-audit/summary.txt` и переносит значения сюда,
> меняя `pending`/`assumed` на `confirmed`. Ничего не выдумывать.

## Как собрать данные

```bash
# 1. Установить platform-tools (один раз) — УЖЕ ВЫПОЛНЕНО на этом хосте
brew install --cask android-platform-tools     # macOS
#   либо https://developer.android.com/tools/releases/platform-tools

# 2. На устройстве: Settings → About → 7× tap Build number → Developer options
#    → включить USB debugging. Подключить кабель, принять "Allow USB debugging".

# 3. Проверить соединение
adb devices          # устройство должно быть в состоянии 'device', не 'unauthorized'

# 4. Запустить аудит (read-only, с редакцией секретов)
./scripts/device_audit.sh

# 5. Открыть artifacts/device-audit/summary.txt и перенести значения в таблицы ниже.
```

## 1. Идентификация и сборка

| Параметр | Источник (`getprop`) | Значение | Статус |
|---|---|---|---|
| Производитель | `ro.product.manufacturer` | Meebook | assumed |
| Модель | `ro.product.model` | M103 (в переписке упоминались M8 и M103 — подтвердить) | assumed |
| Device / codename | `ro.product.device` | — | pending |
| Android release | `ro.build.version.release` | 11 | assumed |
| Android SDK | `ro.build.version.sdk` | 30 (ожидается для Android 11) | assumed |
| Security patch | `ro.build.version.security_patch` | — | pending |
| Fingerprint | `ro.build.fingerprint` | — | pending |
| Board / platform | `ro.board.platform` | — | pending |

## 2. ABI и разрядность (критично для T01)

| Параметр | Источник | Значение | Статус |
|---|---|---|---|
| Основной ABI | `ro.product.cpu.abi` | — | pending |
| Список ABI | `ro.product.cpu.abilist` | — | pending |
| 32-bit ABI | `ro.product.cpu.abilist32` | — | pending |
| 64-bit ABI | `ro.product.cpu.abilist64` | — | pending |
| Zygote | `ro.zygote` | — | pending |

Интерпретация:

- `ro.zygote=zygote32` → userspace **только 32-bit**;
- `ro.zygote=zygote64` или `zygote64_32` → присутствует **64-bit** userspace.

Пока ABI не подтверждён, T01 держит правило плана: **не добавлять нативные
зависимости**, использовать pure Kotlin/Java библиотеки, `minSdk = 30`.

## 3. Экран

| Параметр | Источник | Значение | Статус |
|---|---|---|---|
| Разрешение | `wm size` | — | pending |
| Плотность (DPI) | `wm density` | — | pending |
| Физический дисплей | `dumpsys display` | см. `artifacts/device-audit/display_dumpsys.txt` | pending |

Нужны и портретное, и альбомное подтверждение (T00 → «портрет и альбом»).

## 4. E-ink / vendor-компоненты

| Область | Как искать | Найдено | Статус |
|---|---|---|---|
| Пакеты `eink/epd/boyue/likebook/meebook` | `pm list packages` + grep | — | pending |
| Системные свойства e-ink | `getprop` + grep | — | pending |
| Системные сервисы | `service list` | — | pending |
| Режимы Normal/Regal/A2 | `logcat -d` при ручном переключении | — | pending |
| Полный refresh без root | наблюдение по logcat/сервисам | — | pending |
| App-specific optimization в системных настройках | ручная проверка | — | pending |

Порядок захвата logcat при смене режима: `adb logcat -c`, вручную переключить
Normal → Regal → A2 на устройстве, затем повторно запустить
`scripts/device_audit.sh` и посмотреть `artifacts/device-audit/logcat_eink.txt`.

## 5. Поведение lifecycle (sleep/wake, ориентация)

| Проверка | Метод без готового APK | Результат | Статус |
|---|---|---|---|
| Возврат в открытую Activity после кнопки питания | наблюдение + `dumpsys activity activities` | — | pending |
| Смена ориентации portrait/landscape | `wm size` в обеих ориентациях | — | pending |
| Немедленный refresh в `onResume` | проверяется в T02 на реальном APK | — | pending (T02) |

Полная проверка sleep/wake поведения Activity требует установленного APK и
относится к этапам T01/T02. На T00 фиксируется только доступное без APK.

## 6. Установка APK и Google Calendar sync

| Проверка | Метод | Результат | Статус |
|---|---|---|---|
| Установка debug APK без root | `adb install` (в T01, тестовым APK) | — | pending (T01) |
| Google Play Services / GSF присутствуют | `pm list packages` grep `gsf/gms` | — | pending |
| Google-аккаунт добавлен и Calendar sync включён | ручная проверка в системе (без записи логина) | — | pending |

> Личные данные аккаунта в отчёт **не** переносятся: фиксируется только факт
> «аккаунт есть / sync включён», без адреса почты.

## 7. Ограничения аудита (соблюдены)

- без root;
- без декомпиляции пользовательских приложений;
- без изменения системных настроек, кроме согласованных Developer options;
- Android-проект (T01) не начинается до фиксации ABI;
- вывод обезличен: серийный номер, аккаунты, MAC/ID редактируются скриптом.

## 8. Что разблокирует T01 после заполнения

1. Подтверждённые `model` / `device` / Android SDK.
2. Подтверждённый ABI и разрядность → окончательное решение по native-зависимостям.
3. Разрешение и DPI → базовые раскладки.
4. Список e-ink возможностей и **неизвестных** → стратегия `core/eink` (no-op fallback обязателен).
