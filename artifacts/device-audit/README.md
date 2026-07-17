# artifacts/device-audit

Обезличенные выводы `scripts/device_audit.sh`. Заполняется при запуске аудита
на подключённом устройстве.

Файлы, которые создаёт скрипт:

- `summary.txt` — главный сводный отчёт (идентификация, ABI, экран, e-ink);
- `getprop_full.txt` — полный redacted-снимок системных свойств;
- `packages_all.txt` — список установленных пакетов;
- `services.txt` — имена системных сервисов;
- `display_dumpsys.txt` — `dumpsys display`;
- `logcat_eink.txt` — отфильтрованный logcat по e-ink ключевым словам.

Все значения проходят редакцию (serial, email/аккаунты, MAC, числовые ID).
Файлы данных не коммитятся — см. `.gitignore`; сюда попадает только этот README.
