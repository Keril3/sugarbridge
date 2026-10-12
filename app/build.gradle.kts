plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The version: a release takes it from the git tag (./gradlew -Psugarbridge.version=1.12.0), otherwise this one.
// versionCode grows with it: 1.2.3 → 10203.
val appVersion = providers.gradleProperty("sugarbridge.version").getOrElse("1.12.0")
val appVersionCode = (appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0))
    .take(3)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// The release signing key: environment variables (GitHub Actions) or ~/.gradle/gradle.properties on your own
// computer. The key is not in the repository and must never be, see the README, "Releasing a new version".
fun secret(env: String, property: String): String? =
    providers.environmentVariable(env).orElse(providers.gradleProperty(property)).orNull

val releaseKeystore = secret("SUGARBRIDGE_KEYSTORE", "sugarbridge.keystore")

android {
    namespace = "com.sugarbridge.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sugarbridge.app"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = secret("SUGARBRIDGE_KEYSTORE_PASSWORD", "sugarbridge.keystorePassword")
                keyAlias = secret("SUGARBRIDGE_KEY_ALIAS", "sugarbridge.keyAlias")
                keyPassword = secret("SUGARBRIDGE_KEY_PASSWORD", "sugarbridge.keyPassword")
            }
        }
    }

    // the language is switched inside the app, so an app bundle must keep every language in the base APK
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            // R8 is off: the partial optimization from the Android Studio template (packageScope) built an APK
            // that crashed on start (IllegalAccessError in kotlin.collections). Without R8 the release behaves
            // exactly like the tested debug build; a few extra megabytes don't matter for a personal app.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.json)  // the real org.json instead of the android.jar stubs in JVM tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}