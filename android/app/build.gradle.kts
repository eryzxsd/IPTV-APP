import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Version comes from the root package.json so the APK and the EXE always match.
@Suppress("UNCHECKED_CAST")
val pkg = JsonSlurper().parse(rootProject.file("../package.json")) as Map<String, Any>
val appVersion = pkg["version"] as String
val versionParts = appVersion.split(".").map { it.takeWhile(Char::isDigit).toInt() }

android {
    namespace = "app.lumentv"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lumentv"
        minSdk = 24
        targetSdk = 36
        versionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
        versionName = appVersion
    }

    signingConfigs {
        create("release") {
            // Stable signing via env vars (see README); otherwise falls back to the debug key.
            // CI passes unset secrets as empty strings, so blank counts as "not set".
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            val ks = env("ANDROID_KEYSTORE_FILE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = env("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = env("ANDROID_KEY_ALIAS") ?: "lumentv"
                // PKCS12 keystores use the store password for the key too.
                keyPassword = env("ANDROID_KEY_PASSWORD") ?: env("ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Screenshot test (Robolectric + Roborazzi) only runs when asked to, e.g.
            // LUMEN_SCREENSHOTS=/path/to/out ./gradlew testDebugUnitTest
            System.getenv("LUMEN_SCREENSHOTS")?.let {
                test.systemProperty("roborazzi.test.record", "true")
                test.environment("LUMEN_SCREENSHOTS", it)
            }
            test.environment("LUMEN_REAL_DATA", System.getenv("LUMEN_REAL_DATA") ?: "")
            test.maxHeapSize = "3g"
            // The JVM's regex engine recurses deeply on long HLS attribute lines (Android's ICU engine doesn't).
            test.jvmArgs("-Xss8m")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Latest stable releases: Compose BOM 2026.09.00 -> Material 3 1.4.0, Media3 1.11.1, Material Components 1.14.0.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    // Android TV: View-based Leanback UI (no Compose on TVs, so it stays fast on low-end/32-bit boxes).

    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
