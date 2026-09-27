// llama_jni built with LOCALAI_CPU_VARIANT=i8mm -- see
// app/src/main/cpp/CMakeLists.txt. No sources of its own: the same
// CMakeLists.txt, a different CMake argument, a differently named .so
// (libllama_jni_i8mm.so) that :app packages alongside the baseline
// variant and com.intelliverse.llama.CpuVariant picks at runtime. Ported
// from rmant7/AI's own native-variants/llama-i8mm module.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.intelliverse.nativevariant.llama_i8mm"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DLOCALAI_CPU_VARIANT=i8mm")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../app/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
