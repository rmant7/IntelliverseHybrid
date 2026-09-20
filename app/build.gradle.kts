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

    defaultConfig {
        applicationId = "com.intelliverse"
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
        }
        all {
            buildConfigField("String", "app_metrica_api_key", "\"$appMetricaApiKey\"")
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