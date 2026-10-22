plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Ядро, UI по умолчанию и API плагинов. Хост включает его в APK,
// внешние плагины будут подключать `compileOnly`.
android {
    namespace = "com.zemlianikin.flexiblesearch.core"
    compileSdk = 37

    defaultConfig {
        // android.icu.text.Transliterator для транслита в NameMatcher.
        minSdk = 29
    }

    buildFeatures {
        compose = true
    }

    // android.util.Log в юнит-тестах ничего не делает, а не падает.
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Видны плагинам через PluginScope и компоненты: часть API.
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.okhttp)
    api(libs.okhttp.coroutines)
    api(libs.serialization.json)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
