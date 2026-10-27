pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AppointmentReminder"
include(":core")
// Set REMINDERS_CORE_ONLY=1 to build/test just the pure-Kotlin core without an Android SDK.
if (System.getenv("REMINDERS_CORE_ONLY") == null) include(":app")
