// All plugins are declared once here (version-pinned, not applied); modules apply them by id without a version.
// Gradle requires AGP and the Kotlin plugin to live in the same classloader, i.e. both declared in the root project.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
