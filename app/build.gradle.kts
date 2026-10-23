plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zemlianikin.flexiblesearch"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zemlianikin.flexiblesearch"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // Свой телефон: ставится поверх debug, данные остаются.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // android.util.Log в юнит-тестах ничего не делает, а не падает.
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
