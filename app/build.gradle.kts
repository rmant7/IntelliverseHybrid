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

// Set by CI via -PbuildNumber=<github.run_number> so a build coming off the
// "latest" release can be identified from inside the app itself (Log screen
// header) -- matches the Intelliverse-<run_number>.apk filename in the
// workflow. "local" for anyone building outside CI, where there is no run
// number at all.
val buildNumber = (project.findProperty("buildNumber") as String?) ?: "local"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

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
    compileSdk = 35

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
    }

    defaultConfig {
        // Differs from the other active branch's "com.intelliverse" on
        // purpose (this branch only) so both branches' APKs can be
        // installed on the same device at once instead of colliding as
        // the same package. namespace above stays "com.intelliverse" --
        // only the generated R class package, not the installable
        // identity, so changing it would just ripple through every
        // source file's R imports for no benefit.
        applicationId = "com.intelliverse.matterofchoice"
        minSdk = 23
        targetSdk = 35
        versionCode = 23
        versionName = "1.22"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                //"proguard-rules.pro"
            )
            // Without a signingConfig, assembleRelease produces an unsigned
            // APK that Android's package manager refuses to install at all --
            // fine for a Play Store upload (which signs it itself), useless
            // as the one APK someone is expected to actually install and test.
            // The debug keystore is not a real release signature: this build
            // still isn't suitable for the Play Store, only for installing
            // and testing outside it.
            signingConfig = signingConfigs.getByName("debug")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
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
