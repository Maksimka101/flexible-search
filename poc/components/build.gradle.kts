plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zemlianikin.flexiblesearch.poc.components"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zemlianikin.flexiblesearch.poc.components"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
