import java.util.Properties

// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val isAdvertisementDisabled = localProperties.getProperty("is_advertisement_disabled", "false")
val geminiApiKey = localProperties.getProperty("gemini_api_key")
// Comma-separated, same as gemini_api_key: one key today, a rotated pool later, same property name either way.
val groqApiKey = localProperties.getProperty("groq_api_key")
val gigachatApiKey = localProperties.getProperty("gigachat_api_key")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)

    id("com.google.devtools.ksp")

    // Dagger-Hilt
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.shared"
    compileSdk = 37 // kept in sync with app/build.gradle.kts -- see its own comment

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // GigaChat stays debug-only (see BaseResultViewModel's
            // BuildConfig.DEBUG gate on gigaChat()) -- its network security
            // config trusts a Russian government CA chain (sberbank.ru),
            // unusual enough to risk Play Store review scrutiny, and a
            // release build has no business shipping a key it never calls.
            buildConfigField("String", "gigachat_api_key", "\"\"")
        }
        debug {
            buildConfigField("String", "gigachat_api_key", "\"$gigachatApiKey\"")
        }
        all {
            buildConfigField("boolean", "is_advertisement_disabled", isAdvertisementDisabled)
            buildConfigField("String", "gemini_api_key", "\"$geminiApiKey\"")
            buildConfigField("String", "groq_api_key", "\"$groqApiKey\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    // On-device AI through rmant7/AI's SDK contract (see local-ai-sdk/SOURCE).
    api(project(":local-ai-sdk"))
    // rmant7/AI's engine: llama.cpp JNI, LlamaCppRuntime, RAM admission and measuring (LocalModelEngine).
    api(project(":llama-runtime"))

    // Dagger - Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.navigation.runtime.ktx)
    ksp(libs.dagger.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Media player
    implementation (libs.androidx.media3.exoplayer)
    //Markdown to Html
    implementation(libs.markdown)

    // Coil
    implementation(libs.coil.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Ktor & kotlin Serialization
    // OkHttp engine, not Android: Ktor's "android" engine is backed by the
    // platform's own ancient bundled HttpURLConnection/okhttp-internal
    // classes, which have a long-known bug reusing a pooled keep-alive
    // connection the server already closed -- surfacing as
    // EOFException("unexpected end of stream") or
    // SocketException("Software caused connection abort"), both seen on a
    // real device hitting Gemini specifically, with connectivity otherwise
    // confirmed fine at the time. The real, actively maintained OkHttp
    // engine detects and recovers from a stale connection instead of
    // handing the caller a raw I/O exception.
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.logging.jvm)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.play.services.ads.lite)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.androidx.material3.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}