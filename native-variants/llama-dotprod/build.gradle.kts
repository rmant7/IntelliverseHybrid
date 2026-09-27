// llama_jni built with LOCALAI_CPU_VARIANT=dotprod -- see
// app/src/main/cpp/CMakeLists.txt. No sources of its own: the same
// CMakeLists.txt, a different CMake argument, a differently named .so
// (libllama_jni_dotprod.so) that :app packages alongside the baseline
// variant and com.intelliverse.llama.CpuVariant picks at runtime. Ported
// from rmant7/AI's own native-variants/llama-dotprod module.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.intelliverse.nativevariant.llama_dotprod"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DLOCALAI_CPU_VARIANT=dotprod")
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
