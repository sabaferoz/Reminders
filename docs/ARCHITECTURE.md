# Architecture & security overview

## Modules
```
:core  pure Kotlin/JVM (java.time) – no Android types, fully unit-tested (45 tests)
  Model.kt        settings, rules, patient/appointment/reminder state, MessageDelivery interface (V2 seam)
  Planner.kt      ReminderPlanner: relative/fixed-clock due times, overrides, quiet hours, "never after start"
  Reconciler.kt   ReminderReconciler: appointments+patients+settings+existing → upserts/deletes (cancel, reschedule, dedupe)
                  OutgoingMessageBuilder: final gate at "Open WhatsApp" time
  Templates.kt    MessageRenderer: 5 placeholders, DST-aware zone label, validation
  Matching.kt     AttendeeMatcher: normalized email → patients, staff/organizer excluded, duplicates = ambiguous
  GoogleCalendar.kt  JSON DTOs, Meet-link extraction (structured fields only), AppointmentSync (reschedule/cancel diff)
  TimeZones.kt    country → IANA zone(s); curated US/CA/AU/ES regions; ICU fallback is injected by the app
:app   Android
  data/       Room + SQLCipher (Db.kt), Repository (only class touching the DB), SettingsRepo (JSON in encrypted kv table)
  calendar/   GoogleAuth (Play services Authorization), CalendarApi (read-only GETs), CalendarSync
  work/       Scheduling (AlarmManager + WorkManager + receivers), Notifier
  whatsapp/   WhatsAppHandoff : MessageDelivery (wa.me deep link restricted to com.whatsapp / com.whatsapp.w4b)
  demo/       fictional seed data
  ui/         MainViewModel (single VM) + Compose screens
```
Spring analogy: `:core` = the service layer (no framework), `Repository` = the DAO, `MainViewModel` = a request-scoped controller,
`Graph` in `App.kt` = the ApplicationContext (hand-wired).

## Core flow
`sync` (Calendar → local appointments) → `Repository.reconcile` → `ReminderReconciler` (pure) → rows: SCHEDULED → READY at due time →
notification → *Open WhatsApp* (re-sync, rebuild message from fresh data, hand off) → WHATSAPP_OPENED → *Confirm sent* → SENT_CONFIRMED.
“Ready time” (`readyAt`) and the manager's confirmation (`confirmedAt`) are stored separately; opening WhatsApp is never treated as delivery.

Rules worth remembering: fixed-time A = previous calendar day *in the chosen zone*, B = same day; quiet hours use the patient's zone;
a reminder inside quiet hours moves to the window's end only if that is still before the appointment, otherwise it is SKIPPED with a reason;
A is skipped when B is also already due; nothing is prepared after the start; rescheduling resets reminders for the new time;
cancellation skips open reminders but keeps confirmed history; consent is required before any reminder exists.

## Scheduling limits (Android reality)
`WorkManager` is deferrable (≥15 min period, Doze/battery-saver may delay it). We therefore also arm one `AlarmManager.setAndAllowWhileIdle`
alarm for the next due reminder – accurate to within minutes, no special permission (exact alarms would need `SCHEDULE_EXACT_ALARM` and are unjustified when a human must tap Send).
Boot, clock-change, time-zone-change and app-update broadcasts re-arm it. Aggressive OEM battery managers can still kill background work: the Settings screen links to battery settings, and opening the app always re-syncs.

## Security & privacy
- Permissions: INTERNET, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED. No contacts, SMS, location, camera, mic, accessibility.
- Local DB encrypted with SQLCipher; its random passphrase is wrapped by a non-exportable Android Keystore AES-GCM key.
- Google: read-only scopes, short-lived access tokens fetched on demand (nothing stored), revoked on Disconnect; event titles/descriptions are never requested.
- Backups disabled (`allowBackup=false` + extraction rules exclude everything).
- Notifications are generic (“N reminders are ready to send.”); private on the lock screen with a redacted public version.
- Release builds set `FLAG_SECURE` (no screenshots / recents preview).
- No logging of patient data, no analytics/ads/AI SDKs, no cleartext HTTP. Only hosts contacted: `www.googleapis.com`, `oauth2.googleapis.com` (revoke), Google Play services, and WhatsApp via your explicit tap.
- Retention: appointments and their reminders are deleted 30 days after they end; deleting a patient cascades to their reminders; “Delete all local data” wipes the DB.
- Ambiguity fails safe: duplicate patient emails, missing/invalid phone, missing/non-Meet link, no consent, or a cancelled/started appointment block sending.
- WhatsApp: the message and Meet link go to the recipient (and through WhatsApp/Meta). The app shows this notice before the first send.
- Use fictional data in development; the demo numbers are blank by design.

## Version 2 seam
Implement `MessageDelivery` with the WhatsApp Business Platform API (template messages, delivery webhooks need a server – a deliberate V2 decision) and swap it in `MainViewModel.openWhatsApp`. Message building (`OutgoingMessageBuilder`) and reminder state are already delivery-agnostic.
