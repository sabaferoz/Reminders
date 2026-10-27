# Appointment Reminder Assistant (Android, Kotlin)

Reads appointments from Google Calendar (read-only), converts them to each patient's **local** time, prepares two configurable
WhatsApp reminders per appointment, notifies the manager when they are due, and opens WhatsApp with the message prefilled.
The manager presses Send herself. Everything is stored encrypted on the phone; there is no backend.

> **Build status – read this first**
> | Part | Status |
> |---|---|
> | `:core` (scheduling, time zones, templates, matching, Calendar JSON, reconciler) | ✅ compiled and **45 unit tests pass** |
> | `:app` (Compose UI, Room, WorkManager, Google auth, WhatsApp hand-off) | ⚠️ **written but never compiled or run** – the authoring sandbox had no Android SDK and could not reach Google's Maven repository. Expect to fix a few compile errors on first build. |
> | Debug APK | ❌ not produced yet – build it with the steps below |
> | Real Google sign-in / WhatsApp / notifications on a device | ❌ untested |

## 1. One-time setup (Windows, PowerShell)

```powershell
winget install --id Google.AndroidStudio -e      # bundles JDK 17+ (JBR) and the SDK manager
winget install --id Git.Git -e                   # skip if `git --version` already works
git clone https://github.com/sabaferoz/Reminders.git
cd Reminders
git checkout claude/vibrant-carson-u5l9fy
```
Open the folder in Android Studio → let Gradle sync (it offers to install the Android SDK 35 platform; accept).
Android Studio creates `local.properties` (git-ignored) with your SDK path. No other tools are needed: use the bundled JDK and the Gradle wrapper.

## 2. Install on your phone
1. Phone: Settings → About phone → tap **Build number** 7× → Settings → System → **Developer options** → enable **USB debugging**.
2. Plug in via USB, accept the “Allow USB debugging” prompt. Check: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices`
3. Android Studio → select your phone → ▶ Run. Or from PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```
If Gradle says “SDK location not found”, open the folder once in Android Studio (it writes `local.properties`).

There is no demo data: the app starts empty. Add patients (with consent), connect Google Calendar (below), and reminders appear for matching appointments.
Reminder A/B offsets accept any number of minutes (e.g. 5) if you want to check timing quickly.
Run the logic tests: `.\gradlew.bat :core:test`

## 3. Connect your Google Calendar
Follow [docs/OAUTH.md](docs/OAUTH.md) (≈10 minutes, no secrets go into the repo), then Settings → *Connect Google Calendar*.

## 4. Docs
- [docs/OAUTH.md](docs/OAUTH.md) – Google Cloud setup, SHA-1, Testing-mode limits, sending the APK to the manager, signed release APK.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) – architecture, scheduling limits, security & privacy model.

## Out of scope (by design)
No backend, Firebase, Gmail, Contacts, AI/LLM, analytics, accessibility automation, or WhatsApp Cloud API (Version 2 plugs in behind `MessageDelivery`).
