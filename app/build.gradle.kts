import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sabaferoz.apptreminder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sabaferoz.apptreminder"
        minSdk = 26 // java.time is built in from API 26; no desugaring needed
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Optional shared signing key (so the OAuth SHA-1 stays stable across machines/CI).
    // Pass -PremindersKeystore=... -PremindersKeystorePassword=... -PremindersKeyAlias=... -PremindersKeyPassword=...
    val ksPath = providers.gradleProperty("remindersKeystore").orNull
    if (ksPath != null) {
        signingConfigs {
            create("shared") {
                storeFile = file(ksPath)
                storePassword = providers.gradleProperty("remindersKeystorePassword").get()
                keyAlias = providers.gradleProperty("remindersKeyAlias").get()
                keyPassword = providers.gradleProperty("remindersKeyPassword").get()
            }
        }
    }

    buildTypes {
        debug { if (ksPath != null) signingConfig = signingConfigs.getByName("shared") }
        release {
            isMinifyEnabled = false
            if (ksPath != null) signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)
    implementation(libs.serialization.json)
    implementation(libs.work.runtime)
    implementation(libs.play.auth)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher) { artifact { type = "aar" } }
    implementation(libs.androidx.sqlite)
}
