

// Top-level build file where you can add configuration options common to all sub-projects/modules.


buildscript {
    dependencies {
        classpath (libs.gradle)
        classpath(libs.secrets.gradle.plugin)
        //classpath(libs.google.services)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Room
    id ("androidx.room") version "2.6.1" apply false
    // Dagger-Hilt
    id("com.google.dagger.hilt.android") version "2.58" apply false
    // KSP
    alias(libs.plugins.ksp) apply false
    // Compose compiler — moved out of AGP/kotlinCompilerExtensionVersion and
    // into the Kotlin repo as of Kotlin 2.0; every module below that builds
    // Compose UI now applies this instead.
    alias(libs.plugins.compose.compiler) apply false
    // Serialization
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    // Google Services
    id("com.google.gms.google-services") version "4.4.2" apply false
    alias(libs.plugins.android.library) apply false
}