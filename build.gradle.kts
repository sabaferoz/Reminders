// All Kotlin plugins are declared once here (version-pinned, not applied); modules apply them by id without a version.
// AGP and KSP are versioned in :app only, so `REMINDERS_CORE_ONLY=1 ./gradlew :core:test` works without the Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
