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
        // wordpress-rs, Automattic's WordPress REST API library.
        maven("https://a8c-libs.s3.amazonaws.com/android") {
            content { includeGroup("rs.wordpress.api") }
        }
    }
}

rootProject.name = "WPFolio"
include(":app")
