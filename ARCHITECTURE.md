# Architecture

CRoMan 0.2 is one Android app with five layers. Networking is not implemented. The data shape is the same one a later LAN peer or a cloud backend would sync.

## Layers

1. **Typed core.** Contacts, tasks, activities, calendar events, payments (income, expense, debt), catalog items, documents, and the message log are real tables. Realtor clients are contacts. Listing photos are documents. A task with a due date also writes a calendar event.
2. **Configuration engine.** A workspace is created from a `WorkspaceTemplate` (`template_id` + `template_version`) stored as versioned JSON in `assets/templates`. The template declares entity types, custom fields, pipelines, relations, menu, messengers, and message texts. Field keys are stable and independent of the RU/EN/HE labels. Values live in `field_values` with typed columns (`value_text`, `value_number`, `value_money`, `value_date`, `value_bool`, `value_ref`, `value_json`) and indexes, so filter, sort, and search do not scan a blob. Entities also keep `search_text`, `badge`, `subtitle`, and `money_amount` so lists stay one indexed query. `view_configs` stores the template document for the workspace.
3. **Specialized logic.** Only where the behaviour is not generic. The realtor home counts active listings (`status = active`) and open pipeline stages. Everything else on that home is the same task and entity data.
4. **Country adapters.** `CountryAdapter` decides which messenger ids a country allows. `MessengerGateway` builds the share URL. The contact screen only asks the registry. WhatsApp, Telegram, and MAX are gateways. Israel allows WhatsApp and Telegram. Russia also allows MAX. A workspace country of `*` allows every gateway the template lists.
5. **Data / sync.** Room database `croman.db`, schema version 1, `exportSchema` on, schema committed. v0.1 used another application id, so there is no data migration from it. From 0.2 onward every schema change needs a `Migration` and a new schema file. There is no destructive fallback.

## Record shape

Every row has `id` (UUID), `workspace_id`, `owner_user_id`, `created_by_user_id`, `updated_by_user_id`, `assigned_to_user_id` (nullable), `created_at`, `updated_at`, `deleted_at`. The only user is the local id. There is no permissions UI.

`change_log` stores `table_name`, `subject_id`, `operation` (`upsert` or `delete`), and `changed_at`. A future peer, whether another Android phone on the LAN or a cloud API, can pull `changed_at > cursor`, apply the row when `updated_at` is newer, and treat `deleted_at` as a tombstone. The same tables are the protocol. Repositories are the only writers.

## Template versions

`TemplateMigrations` applies ordered rules between versions: rename a field key (definition and values move together) or split one value into several keys. Rules are data, not code. Realtor ships at version 1 with an empty rule list. The unit test covers rename and split without dropping the original text.

## Backup

Format version 1 is a zip:

- `manifest.json` with `formatVersion`, `app`, `createdAt`
- `database/croman.db`
- `photos/<file>`

Import checks the manifest version and that the database entry starts with the SQLite header, then replaces the local file and photos. `BackupArchive` is plain JVM code. The unit test exports, wipes the files, restores, and compares bytes.

## Messengers

A message row is created as `PREPARED`, moves to `OPENED` when the other app starts, and becomes `SENT` only after the user confirms on return. The app does not read the thread.

## Language

Chrome strings are in `values`, `values-ru`, `values-he`, and `values-iw` (Android still resolves Hebrew resources as `iw`). Field labels and pipeline names come from the template. `AppCompatDelegate.setApplicationLocales` plus `locales_config.xml` switch the app language. `android:supportsRtl="true"`.

## Build

Release workflow runs on tags `v*.*.*` and `workflow_dispatch` only. The asset is `croman-vX.Y.Z.apk`.
