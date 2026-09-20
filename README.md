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

## Несколько AI-моделей параллельно + ротация ключей

Существовавшая система параллельного показа результатов (`AIService`,
`solutionResults: Map<AIService, String?>`, вкладки на экране результата) ничего
не знает о конкретных провайдерах — она просто рендерит все ключи с непустым
значением, поэтому добавление новой модели не требует правок UI.

Кроме Gemini и GPT, параллельно опрашивается:
- **Groq (api.groq.com)** — `GroqUseCase`, тот же OpenAI-совместимый протокол, что
  и у GPT (langchain4j с `baseUrl = https://api.groq.com/openai/v1`), модель
  `meta-llama/llama-4-scout-17b-16e-instruct` (одна из немногих на Groq, реально
  принимающих картинки — их gpt-oss-модели картинки не понимают вовсе), с
  картинками (base64).

**GigaChat (Sber)** — `GigaChatUseCase` — не в общей параллельной группе: это
резервный вариант. `BaseResultViewModel.generateSolutions()` сначала запускает
Gemini, GPT и Groq параллельно и ждёт все три; только если ни один не ответил,
вызывается GigaChat. GigaChat также получает только текст: он не понимает формат
инлайн-картинок, который отправляют остальные провайдеры (см. комментарий в
файле). Требует обмена ключа на OAuth-токен (`GigaChatTokenProvider`) и, на
реальном устройстве, доверия российскому корневому сертификату "Минцифры" — без
него будет `SSLHandshakeException`, это не баг клиента.

Модуль ротации ключей — `shared/.../data/keys/` (`ApiKeyPool.kt`,
`PrefsApiKeyStore.kt`, `BundledApiKeyStore.kt`, `BundledApiKeys.kt`), портирован
из репозитория `rmant7/AI`. Идея: у каждого провайдера свой пул ключей
(`ApiKeyRotator`); при HTTP 429 использованный ключ уходит "на охлаждение" на
24 часа (или на меньший срок, если сам провайдер в ответе назвал точное время),
а следующий вызов автоматически берёт следующий рабочий ключ из пула — без этого
ротацию каждый провайдер реализовывал бы отдельно и по-своему.

Пока пул заполняется только одним, "зашитым" в сборку ключом на провайдера —
из `local.properties`, через `<provider>_api_key`. Значение может быть как
одним ключом, так и списком через запятую — это тот же формат, что и раньше
у `gemini_api_key`, поэтому переход на несколько ключей позже не потребует
переименования свойства:

```properties
gemini_api_key=КЛЮЧ
groq_api_key=КЛЮЧ1,КЛЮЧ2
gigachat_api_key=АВТОРИЗАЦИОННЫЙ_КЛЮЧ_В_BASE64
```

Пользовательский пул ключей (`PrefsApiKeyStore`) уже подключён и имеет приоритет
над "зашитыми" — не хватает только экрана настроек, чтобы пользователь мог сам
вводить свои ключи; сейчас это чистый задел на будущее.

## Сборка APK

Нужны Android SDK (compileSdk 35) и JDK 17. В корне создать `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
gemini_api_key=ВАШ_КЛЮЧ_GEMINI
groq_api_key=ВАШ_КЛЮЧ_GROQ
gigachat_api_key=ВАШ_КЛЮЧ_GIGACHAT
# Ключ AppMetrica (Yandex) для app/IntelliverseApplication.kt. Опционален:
# без него компилируется нормально, а initializeAppMetrica() при старте
# просто пропускает инициализацию (раньше без этой проверки приложение
# падало на каждом запуске — AppMetricaConfig.newConfigBuilder() бросает
# исключение на невалидный ключ, а сама инициализация шла в корутине без
# обработчика ошибок). Читается так же, как три ключа выше — напрямую
# app/build.gradle.kts, не через secrets-gradle-plugin (тот на практике
# генерировал для этого свойства битое пустое значение что с кавычками
# в значении, что без):
app_metrica_api_key=ВАШ_КЛЮЧ_APPMETRICA
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
   `GEMINI_API_KEY_1`, `GROQ_API_KEY_1`, `GIGACHAT_API_KEY_1`, `APP_METRICA_API_KEY` —
   все опциональны для сборки. Без ключа модели она просто не сможет отвечать;
   без ключа AppMetrica аналитика не инициализируется, но приложение работает
   нормально (см. `local.properties` выше).
   Суффикс `_1` — задел на пул из нескольких ключей на провайдера: чтобы добавить
   второй ключ Groq, заведите секрет `GROQ_API_KEY_2` и допишите его в workflow
   через запятую к первому (`ApiKeyRotator` сам разберёт список и будет
   переключаться между ключами при HTTP 429).
2. После завершения workflow — во вкладке Actions у соответствующего run внизу
   будут артефакты `intelliverse-debug-apk` и `intelliverse-release-apk-unsigned`.
3. Debug APK подписан отладочным ключом AGP и сразу ставится на устройство/эмулятор.
   Release APK не подписан — для публикации в Play Store нужно добавить
   `signingConfig` в `app/build.gradle.kts` и секреты с keystore.
