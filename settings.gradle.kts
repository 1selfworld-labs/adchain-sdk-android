rootProject.name = "AdchainSDK-Android"

include(":adchain-sdk")
include(":sample-app")

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
        maven {
            url = uri("https://jitpack.io")
            credentials {
                username = providers.gradleProperty("authToken").orNull
                    ?: System.getenv("JITPACK_AUTH_TOKEN")
            }
        }
    }
}