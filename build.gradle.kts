// Android/KSP/Compose plugins are applied (with versions from the catalog) in :app only,
// so `REMINDERS_CORE_ONLY=1 ./gradlew :core:test` works on machines without the Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
