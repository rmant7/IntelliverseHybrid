# IntelliverseHybrid

Android-приложение Intelliverse (модуль `app` + под-приложения `DietTracker`,
`StyleTranslator`, `OneClickTrip`, `SchoolKiller` + общий модуль `shared`),
выделенное из монорепозитория `IntelliVerse` (ветка `dev`).

## Что сюда НЕ вошло

Из исходного репозитория намеренно не переносились:
- `*Standalone` модули (`DietTrackerStandalone`, `StyleTranslatorStandalone`,
  `OneClickTripStandalone`, `SchoolKillerStandalone`) — отдельные standalone-приложения,
  не являющиеся частью Intelliverse.
- `backend/`, `unified_server/` — серверный код (Python).
- `OneClickTrip_homePage_python/`, `Sarah's_dev/`, `docs/`, `upload_instructions.txt`.

## Что убрано из кода приложения

Приложение больше не обращается к собственному серверу разработчика
(`eyalhoch.pythonanywhere.com`):
- удалён `ServerUpload` (заливка изображений на свой сервер);
- удалён альтернативный путь получения ответа Gemini через `/converse` на своём сервере
  (`geminiServer()` в `BaseResultViewModel`) — рабочий прямой вызов Gemini API
  (`geminiWithinApp()`) остаётся и используется как единственный путь;
- для GPT (OpenAI) картинки теперь кодируются в base64 и отправляются напрямую в OpenAI
  вместо загрузки на свой сервер за публичным URL;
- удалена неиспользуемая отправка жалоб (`sendReportToServer`) на заглушечный адрес
  `your-backend.com`.

Прямые вызовы сторонних AI API (Google Gemini, OpenAI) сохранены — без них
приложение не сможет решать задачи, это его основная функция, а не "свой сервер".

## Сборка APK

Нужны Android SDK (compileSdk 35) и JDK 17. В корне создать `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
gemini_api_key=ВАШ_КЛЮЧ_GEMINI
```

Затем:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Собрать релизный APK можно через `./gradlew assembleRelease` (потребуется настройка
подписи в `app/build.gradle.kts`).

**Важно**: в этой рабочей среде (Claude Code on the web) собрать APK нельзя — здесь нет
Android SDK, а инфраструктурная политика сети блокирует доступ к `dl.google.com`
(оттуда качаются Android Gradle Plugin и сам SDK). Сборку нужно запускать локально
или через CI (например, GitHub Actions) с доступом к серверам Google.

## Сборка через GitHub Actions

Workflow `.github/workflows/build-apk.yml` собирает debug- и (несигнованный)
release-APK при пуше в `main`/`claude/**`, в pull request и вручную (Actions →
Build APK → Run workflow).

1. Repo → Settings → Secrets and variables → Actions → New repository secret:
   имя `GEMINI_API_KEY`, значение — ваш ключ Gemini API. Без него сборка всё равно
   пройдёт, но приложение не сможет обращаться к Gemini.
2. После завершения workflow — во вкладке Actions у соответствующего run внизу
   будут артефакты `intelliverse-debug-apk` и `intelliverse-release-apk-unsigned`.
3. Debug APK подписан отладочным ключом AGP и сразу ставится на устройство/эмулятор.
   Release APK не подписан — для публикации в Play Store нужно добавить
   `signingConfig` в `app/build.gradle.kts` и секреты с keystore.
