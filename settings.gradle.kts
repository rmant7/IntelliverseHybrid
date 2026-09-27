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
include(":SchoolKiller")
include(":MatterOfChoice")

// Extra CPU-feature builds of :app's llama_jni (dotprod, i8mm) -- no sources
// of their own, just a different CMake argument against the same
// CMakeLists.txt. See app/src/main/cpp/CMakeLists.txt and
// com.intelliverse.llama.CpuVariant, which picks the best one at runtime.
for (variant in listOf("llama-dotprod", "llama-i8mm")) {
    include(":$variant")
    project(":$variant").projectDir = file("native-variants/$variant")
}
