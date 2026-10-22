pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "flexible-search"

include(
    ":core",
    ":app",
)

// Эксперименты кладутся в poc/<имя>: отдельный модуль со своим
// applicationId, ставится рядом.
