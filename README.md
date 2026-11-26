# CRoMan

Offline CRM for a small business. Version 0.2 is aimed at an individual real-estate agent and is built so other professions can be added as workspace templates. There is no account and no network permission.

The Android package is `com.intelliverse.croman`. It installs next to Mantel CRM 0.1 (`com.mantelmakher.crm`); the two apps do not share data.

Copyright 2026 Roman Mantelmakher. Apache License 2.0.

## What you can do

- Switch workspaces. The bundled **Realtor** template covers clients, listings (with photos), a deal pipeline (list and board), tasks with local reminders, notes, search, and a home summary. A small **Tutor** template is included to show that the same screens follow configuration.
- Call, and open WhatsApp, Telegram, or MAX with a message you wrote. Which buttons appear depends on the workspace country adapter. The app records prepared / messenger opened / sent (you confirm). It never reads replies.
- Export or import one zip (database + photos) through the system file picker.
- English, Russian, and Hebrew, with right-to-left layout for Hebrew.

## Build

JDK 17+ and Android SDK 35.

```bash
./gradlew assembleDebug
./gradlew test
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Releases

GitHub Actions builds only on tags `v*.*.*` and on a manual run. It publishes `croman-vX.Y.Z.apk` and deletes older releases, tags, and Actions artifacts.

```bash
git tag v0.2.0
git push origin v0.2.0
```

See [ARCHITECTURE.md](ARCHITECTURE.md).

---

# CRoMan

Офлайн-CRM для малого бизнеса. Версия 0.2 рассчитана на частного риелтора и устроена так, чтобы другие профессии добавлялись шаблонами рабочего пространства. Аккаунта нет, разрешения на интернет нет.

Пакет Android: `com.intelliverse.croman`. Приложение ставится рядом с Mantel CRM 0.1 (`com.mantelmakher.crm`) и не делит с ним данные.

© 2026 Roman Mantelmakher. Apache License 2.0.

## Возможности

- Несколько рабочих пространств. Шаблон **Риелтор**: клиенты, объекты с фото, воронка сделок (список и доска), задачи с локальными напоминаниями, заметки, поиск и сводка. Шаблон **Репетитор** показывает, что те же экраны читают конфигурацию.
- Звонок и кнопки WhatsApp, Telegram и MAX с заранее набранным текстом. Набор кнопок задаёт адаптер страны. Журнал: подготовлено / мессенджер открыт / отправлено (подтверждаете вы). Ответы приложение не читает.
- Экспорт и импорт одного zip (база и фото) через системный выбор файла.
- Русский, английский и иврит; для иврита интерфейс справа налево.

## Сборка

```bash
./gradlew assembleDebug
./gradlew test
```

## Выпуски

Сборка только по тегам `v*.*.*` и вручную. Файл релиза: `croman-vX.Y.Z.apk`. Старые релизы, теги и артефакты удаляются.

```bash
git tag v0.2.0
git push origin v0.2.0
```

Подробности — в [ARCHITECTURE.md](ARCHITECTURE.md).
