plugins {
    // Both on the build classpath already through the root (Kotlin Android, serialization -- same artifact, same version).
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// rmant7/AI's :core, vendored (sources: scripts/sync-local-ai-sdk.sh): the
// RuntimeManager every local model load is admitted through, the weights
// load policy, model descriptors. Only this build file is this repository's
// own: Java 8 bytecode like :shared, and this app's library versions.

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
