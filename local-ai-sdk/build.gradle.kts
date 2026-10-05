plugins {
    // On the build classpath already through the root's Kotlin Android plugin (same artifact, same version).
    id("org.jetbrains.kotlin.jvm")
}

// rmant7/AI's local-AI SDK, vendored: the contract this app talks to for
// on-device models -- which are installed, what each can do, what this
// device checked, chat and translation in. Sources are copied verbatim from
// rmant7/AI (see SOURCE); only this build file is this repository's own:
// Java 8 bytecode like :shared, and this app's coroutines version.
// Re-sync with scripts/sync-local-ai-sdk.sh, never by hand.

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
    api(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
