plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.mimax.files"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mimax.files"
        minSdk = 26
        // targetSdk 28 is REQUIRED so Android 10+ still allows executing Termux binaries
        // from the app data dir (same approach as the official Termux F-Droid build).
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            // CI emulator smoke test only: ./gradlew -PwithX86=true
            if (project.hasProperty("withX86")) abiFilters += "x86_64"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(project(":common"))
    implementation(project(":termux-core"))
    implementation(project(":terminal"))
    implementation(project(":filemanager"))
    implementation(project(":media"))
    implementation(project(":archive"))
    implementation(project(":apktools"))
    implementation(project(":romtools"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
