pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "Mi_files"
include(":app", ":common", ":termux-core", ":terminal", ":filemanager", ":media", ":archive", ":apktools", ":romtools")
