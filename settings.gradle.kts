pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Intelliverse"
include(":app", ":DietTracker", ":StyleTranslator", ":OneClickTrip", ":shared")
// rmant7/AI's on-device AI, vendored (see local-ai-sdk/SOURCE): the LocalAi
// contract, its :core (RuntimeManager, admission) and the llama.cpp engine.
include(":local-ai-sdk", ":local-ai-core", ":llama-runtime")
include(":SchoolKiller")
include(":MatterOfChoice")

// Extra CPU-feature builds of :llama-runtime's llama_jni (dotprod, i8mm) -- no
// sources of their own, just a different CMake argument against the same
// CMakeLists.txt. See llama-runtime/src/main/cpp/CMakeLists.txt and
// ai.localstudio.core.runtime.CpuVariant, which picks the best one at runtime.
for (variant in listOf("llama-dotprod", "llama-i8mm")) {
    include(":$variant")
    project(":$variant").projectDir = file("native-variants/$variant")
}
