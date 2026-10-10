plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Версия: при выпуске берётся из git-тега (./gradlew -Psugarbridge.version=1.2.0), иначе — эта.
// versionCode растёт вместе с ней: 1.2.3 → 10203.
val appVersion = providers.gradleProperty("sugarbridge.version").getOrElse("1.1.0")
val appVersionCode = (appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0))
    .take(3)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Ключ подписи release: переменные окружения (GitHub Actions) или ~/.gradle/gradle.properties на своём
// компьютере. В репозитории ключа нет и быть не должно — см. README, «Выпуск новой версии».
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

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            // R8 выключен: частичная оптимизация из шаблона Android Studio (packageScope) собирала APK,
            // который падал при запуске (IllegalAccessError в kotlin.collections). Без R8 release ведёт
            // себя так же, как проверенный тестами debug; лишние мегабайты для личного приложения не важны.
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
    testImplementation(libs.json)  // настоящий org.json вместо заглушек android.jar в тестах на компьютере
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}