plugins {
    alias(libs.plugins.android.application)
    // Kotlin support is built into AGP 9.0+, so no kotlin.android plugin.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.menuupdater.weeklymenu"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.scerial.soonmenu"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Upload key lives outside the repo; its path and password come from ~/.gradle/gradle.properties.
    // Without them the release build is simply left unsigned.
    signingConfigs {
        val storePath = providers.gradleProperty("SOONMENU_UPLOAD_STORE_FILE").orNull
        if (storePath != null) {
            create("upload") {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("SOONMENU_UPLOAD_PASSWORD").get()
                keyAlias = "upload"
                keyPassword = providers.gradleProperty("SOONMENU_UPLOAD_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    // Lets JVM unit tests run code that logs (android.util.Log returns defaults instead of throwing).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // The domain layer reads assets/schedule.json at runtime; JVM unit tests read the same
    // file from the classpath, so it stays the single source of truth for semester hours.
    // shared/translations (en.json) is shared with iOS, so both apps read the one file.
    sourceSets {
        getByName("main") {
            assets.directories.add("../../shared/translations")
        }
        getByName("test") {
            resources.directories.add("src/main/assets")
            resources.directories.add("../../shared/translations")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Board scraping: HTML parsing (Jsoup) over HTTP (OkHttp).
    implementation(libs.jsoup)
    implementation(libs.okhttp)

    // Preferences (default cafeteria, text size, building order).
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)

    // On-device Korean OCR (bundled model; works offline on first run).
    implementation(libs.mlkit.text.korean)

    // Instrumented tests (OCR spike runs on a device/emulator).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
