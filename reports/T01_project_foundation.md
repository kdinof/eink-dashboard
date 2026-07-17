# T01 — Основа Android-проекта и архитектурные решения

## Статус

completed

## Краткий результат

Создан минимальный, **реально собираемый** Android-проект под подтверждённое
устройство T00 (Meebook M103, API 30, arm64-v8a): single-activity Compose,
`minSdk 30`, воспроизводимый toolchain, пять архитектурных пакетов
(`core/dashboard/modules/settings/diagnostics`), подключённый стек (Coroutines,
Room, DataStore, Retrofit/OkHttp, тесты) и ADR по трём фундаментальным решениям.
Все обязательные проверки пройдены на реальном железе: `./gradlew test` (5/5),
`./gradlew assembleDebug`, установка и запуск debug-APK на подключённом M103 —
приложение в foreground, без крэшей. Продуктовые интеграции (Calendar/Todoist/
Weather/полный Dashboard) сознательно не реализованы — это область T02–T05.

## Что реализовано

- **Toolchain с нуля.** На хосте не было ни JDK, ни Gradle, ни Android SDK.
  Установлены (Homebrew): OpenJDK 17, Gradle (для генерации wrapper),
  Android cmdline-tools + `platform-34`, `build-tools;34.0.0`, `platform-tools`.
- **Собираемый проект:** Gradle wrapper 8.9, AGP 8.6.1, Kotlin 1.9.24,
  Compose Compiler 1.5.14, Compose BOM 2024.06.00, version catalog
  (`gradle/libs.versions.toml`).
- **App-модуль** `com.eink.dashboard`: `EinkDashApp` (Application),
  `MainActivity` (single-activity Compose host с плейсхолдер-экраном,
  grayscale, без анимаций), e-ink-safe launch theme, adaptive-иконка
  (anydpi-v26; minSdk 30 → PNG-бакеты не нужны).
- **Пакеты** `core`, `dashboard`, `modules`, `settings`, `diagnostics` заведены
  с документированными границами владения (package-doc файлы). Единственная
  реальная реализация в T01 — `core.DeviceProfile` (подтверждённые константы T00
  + чистая функция `pxToDp`), покрыта unit-тестами.
- **Debug/release build types** без реальных signing secrets: debug —
  default-подпись + `.debug` suffix; release — R8/minify + shrinkResources, но
  signingConfig не задан (внедряется в T09).
- **ABI:** приложение — pure Kotlin, но DataStore тянет крошечный `.so`; добавлен
  `ndk { abiFilters += "arm64-v8a" }` → в APK остаётся только ABI устройства.
- **ADR** `0001-architecture.md`: single-activity Compose, модульный registry,
  отсутствие backend — с обоснованиями и рассмотренными альтернативами.
- **`scripts/check.sh`** — портативный локальный CI-гейт (сам находит JDK 17 и
  SDK), запускает `test` + `assembleDebug`.
- **`README.md`** с командами setup/сборки/установки; **`.gitignore`** (build,
  `.gradle`, `local.properties`, keystore/secrets).

## Изменённые файлы

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` — корневая сборка, репозитории, флаги
- `gradle/libs.versions.toml` — version catalog (все версии и зависимости)
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — Gradle wrapper 8.9
- `app/build.gradle.kts` — конфиг модуля (minSdk 30, compose, abiFilters, deps, build types)
- `app/proguard-rules.pro` — базовые R8-правила
- `app/src/main/AndroidManifest.xml` — single activity, без runtime-permissions
- `app/src/main/java/com/eink/dashboard/EinkDashApp.kt` — Application
- `app/src/main/java/com/eink/dashboard/MainActivity.kt` — single-activity Compose host
- `app/src/main/java/com/eink/dashboard/core/DeviceProfile.kt` — константы T00 + `pxToDp`
- `app/src/main/java/com/eink/dashboard/{core,dashboard,modules,settings,diagnostics}/package-info.kt` — границы владения
- `app/src/main/res/values/{strings,colors,themes}.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable/ic_launcher_foreground.xml` — ресурсы, grayscale-тема, иконка
- `app/src/test/java/com/eink/dashboard/core/DeviceProfileTest.kt` — 5 unit-тестов
- `docs/adr/0001-architecture.md` — ADR
- `scripts/check.sh` — локальный CI-гейт
- `README.md`, `.gitignore` — документация и ignore-правила
- `local.properties` — **gitignored**, машинный путь к SDK (в git не попадает)

## Принятые решения

- **Версии toolchain** зафиксированы под API 30 + pure-Kotlin (T00): AGP 8.6.1 +
  Gradle 8.9 + JDK 17 + Kotlin 1.9.24 / Compose Compiler 1.5.14. Хостовые
  Gradle 9.6.1 / JDK 26 (побочно установлены brew) НЕ используются для сборки —
  билд принудительно на JDK 17 (см. `check.sh`, `README.md`).
- **compileSdk/targetSdk = 34** при `minSdk 30`: современные библиотеки при
  сохранении совместимости с Android 11.
- **`abiFilters = arm64-v8a`**: единственный ABI устройства; убирает лишние
  x86/x86_64/armeabi-v7a `.so` из DataStore. APK совместим с устройством.
- **Пакеты заведены пустыми** (кроме `core.DeviceProfile`): контракты
  `DashboardModule`/registry — это file ownership T02, T01 их не создаёт, чтобы
  не занять чужую область (правило границ плана).
- **`DeviceProfile` как single source of truth** для констант устройства —
  чтобы T02 верстал от dp (936×1248), а не хардкодил пиксели.
- **applicationId debug = `com.eink.dashboard.debug`** (suffix) — устанавливается
  рядом с будущим release-билдом.

## Проверки

- `./gradlew test` — **passed** (5/5, `DeviceProfileTest`; testDebug+testRelease)
- `./gradlew assembleDebug` — **passed** (`app-debug.apk`, ~25 MB)
- `./scripts/check.sh` — **passed** (test + assembleDebug, JDK/SDK автопоиск)
- Установка smoke-APK на Meebook M103 — **passed** (`adb install -r` → Success)
- Запуск на устройстве — **passed**: `mResumedActivity =
  com.eink.dashboard.debug/.MainActivity`, процесс жив, `-b crash` по нашему
  пакету чист (запись gallery3d — реакция vendor-приложения на install, не наш код)
- ABI-совместимость — **passed**: APK содержит только `lib/arm64-v8a/` (ABI устройства)
- Секреты/локальные пути в git — **passed**: `local.properties`, `build/`,
  `.gradle/` в `.gitignore`; `git grep` по staged-дереву — токенов/ключей нет
  (только Homebrew-дефолтные пути в setup-доке `README`/fallback `check.sh`,
  не машинно-уникальные; путь к SDK — только в gitignored `local.properties`)

## Известные ограничения

- **Toolchain установлен агентом на хост** (brew: openjdk@17, gradle,
  android-commandlinetools + SDK-пакеты). Побочно brew подтянул openjdk 26 и
  Gradle 9.6.1 как зависимости; они не участвуют в сборке проекта.
- `assembleRelease` не запускался (release signing — T09); R8 на release-варианте
  не прогонялся, но `compileRelease*` и `testReleaseUnitTest` — зелёные.
- Плейсхолдер-экран `MainActivity` — временный; immersive fullscreen, минутный
  ticker, реальная e-ink-safe тема и dashboard-каркас — T02.
- Instrumented (androidTest) тесты не запускались на устройстве (для T01 не
  требуются; unit-стек достаточен). Стек подключён для T02+.

## Риски и технический долг

- Версии зафиксированы под текущий момент; при обновлении Kotlin ↔ Compose
  Compiler держать пару в соответствии (таблица в ADR/`libs.versions.toml`).
- Security patch устройства старый (2021-06, из T00) — сетевой surface держать
  минимальным; отражено в решении «no backend» (ADR) и передаётся в T08.
- `local.properties` машинно-зависим: на другом хосте нужен свой `sdk.dir`
  (или `ANDROID_SDK_ROOT`) — задокументировано в README и `check.sh`.

## Что должен знать следующий агент

- **Сборка/проверки:** `./scripts/check.sh` (или `./gradlew test assembleDebug`).
  Требует **JDK 17** (AGP 8.6) — `check.sh` находит его сам; иначе
  `JAVA_HOME=<jdk17>`. SDK — из `local.properties`/`ANDROID_SDK_ROOT`.
- **Точка расширения (registry):** пакеты `dashboard/modules/settings/diagnostics`
  заведены и **пусты** — они твои (T02). Контракт `DashboardModule`,
  `ModuleState`, `RefreshPolicy`, `RefreshReason`, `DashboardModuleRegistry`
  ещё НЕ существует: создаёшь его ты. Дизайн-обоснование — в
  `docs/adr/0001-architecture.md` (Decision 2).
- **Константы устройства:** бери из `core.DeviceProfile` (936×1248 dp, 240 dpi,
  grayscale). Не хардкодь пиксели.
- **E-ink:** `core/eink` (feature-flag + no-op fallback) — область T07; в T01 не
  создан. Приложение обязано работать на стоковом рендеринге.
- **Version catalog:** новые зависимости добавляй в `gradle/libs.versions.toml`
  (там уже подключены Room/DataStore/Retrofit/OkHttp/serialization/тест-стек).
- **App id:** debug — `com.eink.dashboard.debug`; namespace/base id —
  `com.eink.dashboard`.

## Следующая задача

`T02` — Платформа дашборда и e-ink-safe UI shell. **Разблокирована**: стабильная
структура проекта, команды проверок (`scripts/check.sh`) и архитектурные
контракты (ADR 0001) переданы; сборка и установка на устройство подтверждены.
