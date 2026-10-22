plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// `./gradlew spotlessApply` форматирует весь проект. Ширина = текстовая
// область
// nvim на разложенном экране: 83 колонки минус номера строк, знаки и
// складки.
val maxLineLength = 76

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(maxLineLength)
        }
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(maxLineLength)
        }
    }
}
