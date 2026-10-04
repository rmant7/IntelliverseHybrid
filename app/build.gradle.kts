import java.util.Properties

// Load local.properties -- same approach as shared/build.gradle.kts, for the
// same reason: com.google.android.libraries.mapsplatform.secrets-gradle-plugin's
// own raw-value insertion turned out to produce an empty (invalid) BuildConfig
// field for app_metrica_api_key even when the local.properties value was
// itself a properly quoted Java string literal -- its exact processing isn't
// documented anywhere reachable from here, so this reads local.properties
// directly instead of depending on it, exactly like shared already does for
// gemini_api_key/groq_api_key/gigachat_api_key.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val appMetricaApiKey = localProperties.getProperty("app_metrica_api_key")

// Real Play Store signing key, written into local.properties by CI from the
// RELEASE_KEYSTORE_BASE64/RELEASE_KEYSTORE_PASSWORD/RELEASE_KEY_ALIAS/
// RELEASE_KEY_PASSWORD repo secrets (see .github/workflows/build-apk.yml).
// Absent locally and on any CI run before those secrets are added -- falls
// back to the debug signingConfig below rather than failing the build, the
// same graceful-degradation pattern this file already uses for API keys.
val releaseKeystorePath = localProperties.getProperty("release_keystore_path")
val releaseKeystorePassword = localProperties.getProperty("release_keystore_password")
val releaseKeyAlias = localProperties.getProperty("release_key_alias")
val releaseKeyPassword = localProperties.getProperty("release_key_password")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()

// A release build signed with the debug key is never a Play artifact. Without
// the real upload key, every release task fails -- unless this is set, which
// CI does only to keep producing an installable tester APK when the signing
// secrets are missing, and then labels the AAB as not for Play.
val allowDebugSignedRelease = (findProperty("allowDebugSignedRelease") as String?).toBoolean()

// Set by CI via -PbuildNumber=<github.run_number> so a build coming off the
// "latest" release can be identified from inside the app itself (Log screen
// header) -- matches the Intelliverse-<run_number>.apk filename in the
// workflow. "local" for anyone building outside CI, where there is no run
// number at all.
val buildNumber = (project.findProperty("buildNumber") as String?) ?: "local"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)

    // Room
    id("androidx.room")
    // KSP
    id("com.google.devtools.ksp")
    // Dagger-Hilt
    id("com.google.dagger.hilt.android")
    // Secrets Gradle Plugin
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

// The secrets plugin scans local.properties by default and inserts each
// value verbatim as BuildConfig source. These four keys are read manually
// above/by shared's own build.gradle.kts instead (which quotes them itself)
// -- letting this plugin also auto-generate a same-named, unused field from
// the same local.properties value produced invalid Java in practice for
// more than one of them, whether the value was empty or already quoted.
secrets {
    ignoreList.add("gemini_api_key")
    ignoreList.add("groq_api_key")
    ignoreList.add("gigachat_api_key")
    ignoreList.add("app_metrica_api_key")
}

android {
    namespace = "com.intelliverse"
    // 37 = Android 17 (released 2026-06-16), the current maximum -- Google
    // Play already requires targeting at least API 36 for new apps/updates
    // as of 2026-08-31, so 35 fell below that floor, not just "not the
    // newest".
    compileSdk = 37
    // The NDK AGP 8.13 already picked by default (CI log: "NDK (Side by side)
    // 27.0.12077973") -- pinned so the llama.cpp/JNI build only changes NDK
    // when this line does, not as a side effect of an AGP bump. r27 links
    // with the 16 KB max-page-size set in app/src/main/cpp/CMakeLists.txt.
    ndkVersion = "27.0.12077973"

    signingConfigs {
        // AGP's built-in "debug" signingConfig otherwise falls back to
        // ~/.android/debug.keystore, auto-generated on first use with a
        // RANDOM key if it doesn't already exist. On GitHub Actions that
        // file never exists (every run is a fresh VM), so every CI build
        // used to get a different signing key -- installing a new build
        // over an old one then fails as a signature mismatch
        // (INSTALL_FAILED_UPDATE_INCOMPATIBLE) unless the old one is
        // uninstalled first. Pointing "debug" at a keystore committed to
        // the repo instead makes every build -- local or CI -- share the
        // same key, so installs upgrade normally.
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Only registered once the real keystore is actually present --
        // referencing a signingConfigs entry that doesn't exist is a Gradle
        // configuration error, so this can't be an always-present config
        // with blank credentials.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.intelliverse"
        minSdk = 26
        targetSdk = 37
        versionCode = 27
        versionName = "1.26"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Local on-device translation inference (llama.cpp via JNI, see
        // src/main/cpp/) -- arm64-v8a only, matching the source project this
        // was ported from (rmant7/AI): its own real-device testing never
        // covered any other ABI, and this app's Play-listed audience is
        // effectively all arm64 anyway.
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

    buildTypes {
        release {
            // R8: shrink + obfuscate + optimize the whole program (library
            // modules stay isMinifyEnabled = false -- the app module's R8
            // pass already covers their code). Every keep rule is in
            // proguard-rules.pro with the specific runtime lookup that
            // needs it; mapping.txt for each build is uploaded by CI.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Real upload-key signature once RELEASE_KEYSTORE_BASE64 etc. are
            // set (see the secrets read above) -- otherwise falls back to
            // the debug keystore so the build still produces something
            // installable for testing, just not Play-eligible.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        all {
            buildConfigField("String", "app_metrica_api_key", "\"$appMetricaApiKey\"")
            buildConfigField("String", "BUILD_NUMBER", "\"$buildNumber\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {

    // Dagger - Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.dagger.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.hilt.android)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.navigation.compose)
    implementation(project(":SchoolKiller"))
    implementation(project(":DietTracker"))
    implementation(project(":StyleTranslator"))
    implementation(project(":OneClickTrip"))
    implementation(project(":MatterOfChoice"))
    implementation(project(":shared"))
    // llama_jni's CPU-feature variants (see app/src/main/cpp/CMakeLists.txt
    // and com.intelliverse.llama.CpuVariant) -- packaged alongside the
    // baseline build from :app's own externalNativeBuild above.
    implementation(project(":llama-dotprod"))
    implementation(project(":llama-i8mm"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.play.services.ads.lite)
    implementation(libs.firebase.common.ktx)
    implementation(libs.analytics)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// See allowDebugSignedRelease above. preReleaseBuild runs before every
// release task (assembleRelease, bundleRelease, install...), so this guards
// all of them in one place.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (!hasReleaseSigning && !allowDebugSignedRelease) {
            throw GradleException(
                "Release build without the release/upload keystore (release_keystore_path in local.properties). " +
                    "A debug-signed release is not a Play artifact; pass -PallowDebugSignedRelease=true only for a tester build."
            )
        }
    }
}
