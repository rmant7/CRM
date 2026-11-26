# Architecture

Mantel CRM 0.1 is a single-device Android app. The shape of the code is meant to survive the next two steps without a rewrite: a file-level backup/restore, then one phone acting as a LAN server for other phones, and later a hosted backend.

## Layers

```
ui  →  domain  ←  data
```

- `domain` has no Android framework types. Models, pure rules (search, money, dates, dashboard), and repository interfaces live here.
- `data` implements those interfaces with Room on one SQLite file, DataStore preferences, photo files, and `AlarmManager` reminders.
- `ui` is Jetpack Compose. View models talk only to the repository interfaces (and to `PhotoStore` when copying a picture the user just picked).
- `AppContainer` is manual dependency injection. A Hilt graph, or a remote data module, can replace it without touching screens as long as the interfaces stay.

## One database, ready for sync

File: `mantel_crm.db` (Room `CrmDatabase`, version 1).

Tables: `clients`, `properties`, `property_photos`, `deals`, `tasks`, `activities`.

Every row has:

- `id` — UUID string, generated on the device. No autoincrement. Two devices can create rows without colliding, and a future server can keep the same id.
- `createdAt` / `updatedAt` — epoch millis. `save()` keeps the original `createdAt`.
- `deletedAt` — null while the row is live. The UI deletes by setting this tombstone. Queries that feed the UI filter `deletedAt IS NULL`. A later sync can ship the tombstone instead of dropping the row.

`exportSchema = true`. The checked-in schema is `app/schemas/com.mantelmakher.crm.data.local.CrmDatabase/1.json`. There is no destructive migration fallback. Version 2 must ship a `Migration(1, 2)`.

Photos are files under `filesDir/property-photos/`, not blobs in SQLite. The photo row stores the file name. Deleting a photo tombstones the row and removes the file.

Text search runs in the repository, in Kotlin, over the active rows. SQLite `LIKE` is not case-insensitive for Cyrillic; Hebrew has no case, but Russian search has to fold case. A remote implementation can replace this with server search behind the same `observe(query, …)` methods.

## Backup, then LAN, then a backend

Not in this version. The seams are already there:

1. **Backup / restore.** Copy `mantel_crm.db` (and the `property-photos` directory), or export the six tables as JSON. UUIDs and tombstones make a later merge possible. Android backup rules already include the database and the photo directory for device transfer.
2. **LAN.** One device can host the same repository operations over the local network. UI stays. Swap `Room*Repository` for a client that calls the host.
3. **Hosted backend.** Same interfaces. `deletedAt` and `updatedAt` are the sync cursor. Conflict policy can be “latest `updatedAt` wins” per row, with tombstones winning over older edits.

`ReminderScheduler` is a port. The alarm implementation is local. A sync client should reschedule from the tasks it just pulled (`TaskRepository.rescheduleAll()` already does that on startup and after boot).

## Language and RTL

User-facing copy is in `res/values` (English), `res/values-ru`, and `res/values-he`. The same Hebrew file is also in `res/values-iw`: current Android still resolves the Hebrew locale to the legacy `iw` resource qualifier, so a `he` folder alone leaves the layout right-to-left while the strings stay in the default language.

- `res/xml/locales_config.xml` registers `en`, `ru`, and `he` for the Android 13 per-app language API.
- The in-app switcher calls `AppCompatDelegate.setApplicationLocales`, which is the fallback below Android 13 and also updates the system setting on 13+.
- The choice is stored in DataStore (`language_tag`) and applied again on the next cold start. `autoStoreLocales` is enabled so AppCompat can restore it before the first frame.
- `android:supportsRtl="true"`. Hebrew (`he` / legacy `iw`) flips layout direction. The UI uses start/end alignment. Phone numbers, emails, and amounts are forced LTR so they stay readable inside an RTL screen.

## What is intentionally local

The manifest does not request `INTERNET`. Call, email, and WhatsApp are intents to other apps. Task reminders use inexact `AlarmManager.setAndAllowWhileIdle` plus a notification channel. They are best-effort under Doze; they do not need a server. After reboot, `BootReceiver` reschedules future reminders. A force-stop clears alarms until the user opens the app again — that is Android policy.

## Build and release

Debug builds are what we ship for sideloading in this stage. GitHub Actions builds only on tags `v*.*.*` and on `workflow_dispatch`. It does not build on every push or pull request. The workflow publishes one APK on a GitHub Release and deletes older releases, their tags, and Actions artifacts so only the latest build remains.
