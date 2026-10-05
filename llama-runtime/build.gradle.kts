plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// rmant7/AI's on-device model engine, vendored (sources:
// scripts/sync-local-ai-sdk.sh): llama.cpp's JNI bridge and its native build
// (llama_jni, 16 KB pages), LlamaCppRuntime (load, generate, the weights
// load mode, a vision projector), RAM measuring and LocalModelEngine, which
// assembles them behind one RuntimeManager. The dotprod/i8mm builds of
// llama_jni come with it (native-variants/). Only this build file is this
// repository's own.

android {
    namespace = "ai.localstudio.llama"
    compileSdk = 37
    // Same NDK as :app pins, so the native build only changes when that line does.
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
}

dependencies {
    api(project(":local-ai-core"))
    implementation(libs.kotlinx.coroutines.android)
    // The dotprod and i8mm builds of llama_jni, picked at runtime by CpuVariant.
    implementation(project(":llama-dotprod"))
    implementation(project(":llama-i8mm"))
}
