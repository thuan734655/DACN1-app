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

rootProject.name = "DACN1-app"

include(
    ":app",
    ":core:designsystem",
    ":core:ui",
    ":core:model",
    ":core:navigation",
    ":core:network",
    ":core:repository",
    ":feature:onboarding",
    ":feature:document",
    ":feature:selfie",
    ":feature:liveness",
    ":feature:verification",
    ":feature:historysupport"
)
