# Google Calendar setup (read-only)

The app uses Google Play services' *Authorization* API: **no client secret, no refresh token and no credentials are stored in the app or the repo.**
Google identifies the app by *package name + signing-certificate SHA-1*.

Package name: `com.sabaferoz.apptreminder`

## Scopes requested
| Scope | Why |
|---|---|
| `https://www.googleapis.com/auth/calendar.events.owned.readonly` | Read events on calendars you own (narrowest scope that works for `events.list`). If the manager's appointments live on a calendar she does *not* own, change it to `calendar.events.readonly` in `GoogleAuth.kt`. |
| `https://www.googleapis.com/auth/calendar.calendarlist.readonly` | List calendars so she can pick the appointment calendar. |

⚠️ OAuth scopes are per *Google account*, not per calendar. The token could technically read events on every calendar the account owns;
selecting one calendar only limits what this app **requests**. The app also never downloads event titles/descriptions (`fields=` filter) and has no write code path.

## Steps
1. https://console.cloud.google.com → project selector → **New project** (e.g. `appointment-reminders`).
2. **APIs & Services → Library** → search **Google Calendar API** → **Enable**.
3. **Google Auth Platform** (or *OAuth consent screen*) → **Get started**: app name, support email → Audience **External** → contact email → agree → Create.
4. **Audience → Test users → Add users**: add your Google address (and later the manager's). Publishing status stays **Testing**.
5. **Data access → Add or remove scopes** → add the two scopes above → Save.
6. Get the SHA-1 of the key that signs the APK (PowerShell, after the first debug build so the keystore exists):
```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android | Select-String "SHA1"
```
   (or `.\gradlew.bat :app:signingReport`)
7. **Clients → Create client** → type **Android** → package name above → paste the SHA-1 → Create. (Nothing to copy into the code.)
8. Run the app → Settings → turn off Demo mode / *Connect Google Calendar* → pick your account → accept the “unverified app” notice (you are a listed test user) → *Select calendar*.

Troubleshooting: *“Error 10 / DEVELOPER_ERROR / 403”* = package name or SHA-1 mismatch, Calendar API not enabled, or the account is not a test user.

## Testing mode vs. the manager's phone
- In **Testing** status, Google expires the grant after **7 days** – the app then shows “Reconnect” and she re-consents weekly. Max 100 test users.
- To stop weekly expiry you must set **Publishing status → In production**. Calendar scopes are *sensitive*, so an unverified production app shows a
  “Google hasn't verified this app” screen (Advanced → Continue) and is capped at 100 users. Full verification needs a privacy policy, a demo video and a few weeks.
  For one clinic, “In production, unverified” is the pragmatic route; if her account is Google Workspace in the same organisation as the project, choose **Internal** and none of this applies.
- The APK on her phone must be signed with a key whose SHA-1 is registered in step 7 (you can add several Android clients to one project).
  Simplest: create **one** keystore, use it for every build (debug and release) and register only its SHA-1:
```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore C:\keys\reminders.jks -alias reminders -keyalg RSA -keysize 2048 -validity 10000
```
  Keep `reminders.jks` out of Git (it is in `.gitignore`) and back it up – losing it means a new SHA-1 and reinstalling.

## Signed release APK (later)
```powershell
.\gradlew.bat :app:assembleRelease "-PremindersKeystore=C:\keys\reminders.jks" "-PremindersKeystorePassword=***" "-PremindersKeyAlias=reminders" "-PremindersKeyPassword=***"
# output: app\build\outputs\apk\release\app-release.apk
```
(Put the four `remindersKeystore*` lines in `%USERPROFILE%\.gradle\gradle.properties` to avoid typing passwords.) Release builds hide the app from screenshots/recents (`FLAG_SECURE`).
Before real use, consider enabling R8 (`isMinifyEnabled = true`) and testing again.
