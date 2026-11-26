# CRoMan roadmap

CRoMan is a mobile CRM for independent specialists and small teams. It adapts to the way they already work. It is not a CRM built for one profession.

## Architecture

Four process engines, each implemented as its own code, not as a generic workflow builder:

1. **Appointment / visit** — tutors, beauty, health, sport, pets.
2. **Object / deal** — real estate, and later cars, hotels, rentals, insurance.
3. **Request / field service** — home repair, car service, detailing, cleaning.
4. **Project / order** — events, tourism, IT, professional services.

A category is not a process. One workspace has a primary process taken from the category chosen at creation, and settings can turn on more processes later. A car workshop can use field service for repairs and object/deal for selling a car, without a second CRM.

New industries are added mainly as templates (fields, stages, tab labels in Russian, English, and Hebrew), not as separate app versions.

## Rule

Do not create an abstraction until at least two real scenarios need it. Extract a shared module only when a second engine actually needs the same behavior. The first shared module is scheduling, packages, and reminders, because tutors and beauty specialists both need it.

## Order of work

Development follows concrete industry tasks, in this order:

1. Real estate and tutors (in the app now).
2. Beauty and health (appointment templates, in the app now).
3. Home repair and car service (field-service templates, in the app now, kept small).
4. Events, tourism, and professional services (project/order templates, in the app now, kept small).

Manufacturing, wholesale, logistics, HR, and NGOs wait for the server/ERP stage.

## Infrastructure

Local only, then backup (done in 0.2), then merge of two devices' backups (done in 0.3.1, the precursor to sync), then one Android device as a LAN server, then cloud/server versions.

---

# Дорожная карта CRoMan

CRoMan — мобильная CRM для независимых специалистов и небольших команд. Она подстраивается под их процесс, а не под одну профессию.

## Архитектура

Четыре процессных движка, каждый своим кодом, без конструктора произвольных процессов:

1. **Запись / визит** — репетиторы, красота, здоровье, спорт, животные.
2. **Объект / сделка** — недвижимость, позже авто, отели, аренда, страхование.
3. **Заявка / выезд** — ремонт дома, автосервис, детейлинг, клининг.
4. **Проект / заказ** — события, туризм, IT, профессиональные услуги.

Категория — это не процесс. У пространства один основной процесс из категории при создании, а в настройках можно включить ещё процессы. Автосервис может вести ремонт как заявки и продажу машины как объект/сделку, не заводя вторую CRM.

Новые отрасли добавляются в основном шаблонами (поля, стадии, подписи вкладок на русском, английском и иврите), а не отдельными версиями приложения.

## Правило

Не вводить абстракцию, пока её не подтвердили хотя бы два живых сценария. Общий модуль появляется только когда второй движок реально нуждается в том же поведении. Первый такой модуль — расписание, пакеты и напоминания: они нужны и репетитору, и мастеру красоты.

## Порядок работ

Разработка идёт от конкретных отраслевых задач:

1. Недвижимость и репетиторы (уже в приложении).
2. Красота и здоровье (шаблоны записей, уже в приложении).
3. Ремонт дома и автосервис (шаблоны заявок, уже в приложении, без лишнего).
4. События, туризм и профессиональные услуги (шаблоны проектов, уже в приложении, без лишнего).

Производство, опт, логистика, HR и НКО отложены до серверной / ERP-стадии.

## Инфраструктура

Только локально, затем резервная копия (сделано в 0.2), затем слияние копий двух устройств (сделано в 0.3.1, подготовка к синхронизации), затем один Android как сервер в локальной сети, затем облачные / серверные версии.
