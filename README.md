# Mantel CRM

Offline CRM for a small business, aimed first at an individual real-estate agent. Version 0.1 runs entirely on one Android phone: no account, no server, no network permission.

The same codebase is structured so a later backup file, a phone that serves other phones on the LAN, and eventually a hosted backend can plug in behind the existing repository interfaces. See [ARCHITECTURE.md](ARCHITECTURE.md).

Copyright 2026 Roman Mantelmakher. Licensed under the Apache License 2.0.

## What you can do

- **Clients** — buyer, seller, tenant, landlord, or other. Call, WhatsApp, and email open the matching app. Search matches names in Russian, Hebrew, and Latin script, and it matches phone digits.
- **Listings** — sale or rent, type, address, price, area, rooms, status, and photos copied from the device into app storage.
- **Deals** — a pipeline (lead → showing → negotiation → contract → won / lost) that links a client and a listing. List or board.
- **Tasks** — due dates and local notifications. They still fire with no internet, within Android’s power limits.
- **History** — notes, calls, messages, meetings, and viewings on a client, listing, or deal.
- **Home** — counts, pipeline, tasks that are due, recent deals.
- **Languages** — English, Russian, and Hebrew. Hebrew uses a right-to-left layout. Switch language inside the app (Android 13+ per-app locales, with an AppCompat fallback on older versions). Light and dark follow the system theme.

Sample data (a small Tel Aviv / Haifa book) can be loaded and hidden from Settings. Loading it again updates the same records.

Removing a record hides it. The row stays in the database with a deletion timestamp so a future sync can propagate the delete.

## Requirements

- JDK 17 or newer
- Android SDK 35 (the Gradle build uses `compileSdk` 35, `minSdk` 26)
- An Android 8.0+ device or emulator to install the app

## Build and test

```bash
./gradlew assembleDebug
./gradlew test
```

The debug APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The app id is `com.mantelmakher.crm`.

## Releases

GitHub Actions does **not** build on ordinary pushes, pull requests, or a schedule. A build runs only when a version tag `v*.*.*` is pushed, or when someone starts the workflow by hand.

The workflow builds a debug-signed APK (no repository secrets), attaches it to a GitHub Release, and deletes older releases together with their tags and Actions artifacts. Only the latest build is kept.

To publish:

```bash
git tag v0.1.0
git push origin v0.1.0
```

---

# Mantel CRM

Офлайн-CRM для малого бизнеса, в первую очередь для частного риелтора. Версия 0.1 целиком живёт на одном телефоне Android: без аккаунта, без сервера и без разрешения на интернет.

Код устроен так, чтобы дальше без переписывания добавить файл резервной копии, телефон-сервер в локальной сети и затем настоящий бэкенд. Интерфейсы репозиториев уже отделены от Room. Подробности — в [ARCHITECTURE.md](ARCHITECTURE.md).

© 2026 Roman Mantelmakher. Лицензия Apache 2.0.

## Что умеет приложение

- **Клиенты** — покупатель, продавец, арендатор, арендодатель или другой тип. Звонок, WhatsApp и почта открывают соответствующие приложения. Поиск понимает русские и еврейские имена и цифры телефона.
- **Объекты** — продажа или аренда, тип, адрес, цена, площадь, комнаты, статус и фотографии с устройства (копируются в память приложения).
- **Сделки** — воронка: лид → показ → переговоры → договор → успех / отказ. Сделка связывает клиента и объект. Есть список и доска.
- **Задачи** — срок и локальное уведомление. Напоминание срабатывает без интернета, насколько это позволяет режим энергосбережения Android.
- **История** — заметки, звонки, сообщения, встречи и показы у клиента, объекта или сделки.
- **Главная** — сводка, воронка, ближайшие задачи и недавние сделки.
- **Языки** — русский, английский и иврит. Для иврита интерфейс справа налево. Язык переключается в приложении (API языка для приложения на Android 13+ и запасной путь через AppCompat). Светлая и тёмная тема берутся из системы.

В настройках можно загрузить и скрыть демонстрационные данные (небольшая подборка по Тель-Авиву и Хайфе). Повторная загрузка обновляет те же записи, а не плодит копии.

Удаление прячет запись. Строка остаётся в базе с отметкой времени удаления, чтобы будущая синхронизация могла передать этот факт.

## Сборка

Нужны JDK 17+ и Android SDK 35. Минимальная версия Android — 8.0.

```bash
./gradlew assembleDebug
./gradlew test
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Установка: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Идентификатор приложения: `com.mantelmakher.crm`.

## Выпуски

GitHub Actions **не** собирает проект на каждый push, pull request или по расписанию. Сборка запускается только по тегу версии `v*.*.*` или вручную (`workflow_dispatch`).

Сборка подписывает APK отладочным ключом (секреты не нужны), прикрепляет его к GitHub Release и удаляет предыдущие релизы, их теги и артефакты Actions. Остаётся только последний выпуск.

```bash
git tag v0.1.0
git push origin v0.1.0
```
