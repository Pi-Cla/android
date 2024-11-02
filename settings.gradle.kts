pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
        maven {
            name = "snapshot"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }
}
rootProject.name = "Revolt"
include(":app")
