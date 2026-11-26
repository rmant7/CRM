# CRoMan

Offline CRM for independent specialists and small teams. Version 0.3.1 runs on Android 6.0 through Android 16 and can merge a backup from another device. There is no account and no network permission. The product plan is in [ROADMAP.md](ROADMAP.md).

The Android package is `com.intelliverse.croman`. It installs next to Mantel CRM 0.1 (`com.mantelmakher.crm`); the two apps do not share data.

Copyright 2026 Roman Mantelmakher. Apache License 2.0.

## What you can do

- Switch workspaces. Creating one asks for a name, and a second workspace of the same template needs its own name. You can rename or hide a workspace. The **Realtor** template covers clients, listings (with photos), a deal pipeline (list and board), tasks with local reminders, notes, search, and a home summary. The **Tutor** template uses student fields and lessons (date, duration, weekly repeat, status, payment, package balance).
- Call, and open WhatsApp, Telegram, or MAX with a message you wrote. Which buttons appear depends on the workspace country adapter. The app records prepared / messenger opened / sent (you confirm). It never reads replies.
- Export or import one zip (database + photos) through the system file picker. A password is optional. If you set one and forget it, that backup cannot be recovered. Import can replace everything or merge into the data already on the device (the default when moving records from one person to another). Merging the same backup twice changes nothing. This merge is the step before a later LAN sync.
- English, Russian, and Hebrew, with right-to-left layout for Hebrew.

## Build

JDK 17+ and Android SDK 35.

```bash
./gradlew assembleDebug
./gradlew test
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Releases

GitHub Actions builds only on tags `v*.*.*` and on a manual run. It runs `./gradlew test` before the APK and publishes `croman-vX.Y.Z.apk`. Older releases, tags, and Actions artifacts are deleted.

```bash
git tag v0.3.0
git push origin v0.3.0
```

## Signing

Every release is signed with `signing/croman-test.keystore` (alias `croman`, store password `croman-test`, key password `croman-test`). This is a **test** key committed so CI always produces the same certificate. It is not a Play Store / production key. If the file is missing, the build fails. It does not generate a random debug key.

0.2.0 and 0.2.1 were signed by whatever debug key the GitHub-hosted runner created for that job. v0.2.0's APK was deleted by the keep-latest workflow, so the two certificates cannot be compared anymore. v0.2.1's certificate is the Android debug identity (`CN=Android Debug`) with SHA-256 `c5d2660a52ee5d181095e580dc0185442267299148ed670d2826944692302bb0`. A fresh runner does not reuse that key. **Uninstall 0.2.0 and 0.2.1 once before installing 0.2.2.** Later builds that use this same test key install over 0.2.2.

To switch to a private key stored in GitHub Actions:

1. Create a keystore and do not commit it.
2. `base64 -w0 your.keystore`
3. Add repository secrets `CROMAN_KEYSTORE_BASE64`, `CROMAN_STORE_PASSWORD`, `CROMAN_KEY_PASSWORD`, and `CROMAN_KEY_ALIAS`.
4. The release workflow uses those secrets when all four are set. The first build with a new key will not install over builds signed with the test key.

## Firebase Test Lab

The tag workflow builds instrumented tests, then runs them on Test Lab only when both of these exist:

1. Secret `GCP_SA_KEY`: the JSON key of a service account that can run Firebase Test Lab.
2. Secret `FIREBASE_PROJECT_ID`, or repository variable `FIREBASE_PROJECT_ID`.

If either is missing, the step logs a skip and the APK is still published. When both are set, the workflow runs:

- instrumented UI tests (realtor home, tutor students) on a virtual Android 14 phone and a virtual Android 9 phone
- a Robo crawl on Android 14 in English, Russian, and Hebrew (`iw`), plus Android 9 in English

Screenshots and video are in the Test Lab results bucket printed by `gcloud`.

See [ARCHITECTURE.md](ARCHITECTURE.md).

---

# CRoMan

Офлайн-CRM для независимых специалистов и небольших команд. Версия 0.3.1 работает с Android 6.0 по Android 16. Аккаунта нет, разрешения на интернет нет. План продукта — в [ROADMAP.md](ROADMAP.md).

Пакет Android: `com.intelliverse.croman`. Приложение ставится рядом с Mantel CRM 0.1 (`com.mantelmakher.crm`) и не делит с ним данные.

© 2026 Roman Mantelmakher. Apache License 2.0.

## Возможности

- Несколько рабочих пространств. При создании нужно имя, второе пространство того же шаблона — со своим именем. Пространство можно переименовать или скрыть. Шаблон **Риелтор**: клиенты, объекты с фото, воронка сделок (список и доска), задачи с локальными напоминаниями, заметки, поиск и сводка. Шаблон **Репетитор**: поля ученика и уроки (дата, длительность, еженедельный повтор, статус, оплата, баланс пакета).
- Звонок и кнопки WhatsApp, Telegram и MAX с заранее набранным текстом. Набор кнопок задаёт адаптер страны. Журнал: подготовлено / мессенджер открыт / отправлено (подтверждаете вы). Ответы приложение не читает.
- Экспорт и импорт одного zip (база и фото) через системный выбор файла. Пароль необязателен. Если задать его и забыть, эту копию нельзя восстановить. При импорте можно заменить всё или слить с данными на устройстве (так по умолчанию передают записи другому человеку). Повторное слияние той же копии ничего не меняет. Это шаг перед будущей синхронизацией по локальной сети.
- Русский, английский и иврит; для иврита интерфейс справа налево.

## Сборка

```bash
./gradlew assembleDebug
./gradlew test
```

## Выпуски

Сборка только по тегам `v*.*.*` и вручную. Сначала `./gradlew test`, затем APK `croman-vX.Y.Z.apk`. Старые релизы, теги и артефакты удаляются.

```bash
git tag v0.3.0
git push origin v0.3.0
```

## Подпись

Релизы подписываются файлом `signing/croman-test.keystore` (alias `croman`, пароли `croman-test`). Это тестовый ключ в репозитории, один и тот же для всех сборок. Для Play Store он не годится. Если файла нет, сборка падает и не создаёт случайный debug-ключ.

0.2.0 и 0.2.1 подписаны ключом, который раннер GitHub сгенерировал для той задачи. **Удалите 0.2.0 и 0.2.1 один раз перед установкой 0.2.2.** Дальше сборки с этим ключом обновляются поверх 0.2.2.

Секреты `CROMAN_KEYSTORE_BASE64`, `CROMAN_STORE_PASSWORD`, `CROMAN_KEY_PASSWORD`, `CROMAN_KEY_ALIAS` переключают CI на свой ключ. Первая сборка с новым ключом не встанет поверх тестовой подписи.

## Firebase Test Lab

Шаг в релизе запускается, только если заданы секрет `GCP_SA_KEY` и секрет или переменная `FIREBASE_PROJECT_ID`. Иначе он пишет, что пропущен, и APK всё равно публикуется. При включении: инструментальные тесты на Android 14 и Android 9 и Robo на Android 14 (en, ru, iw) и Android 9 (en).

Подробности — в [ARCHITECTURE.md](ARCHITECTURE.md).
