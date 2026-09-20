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

    id("com.google.devtools.ksp")

    // Dagger-Hilt
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.shared"
    compileSdk = 37 // kept in sync with app/build.gradle.kts -- see its own comment

    defaultConfig {
        minSdk = 23

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
        }
        all {
            buildConfigField("boolean", "is_advertisement_disabled", isAdvertisementDisabled)
            buildConfigField("String", "gemini_api_key", "\"$geminiApiKey\"")
            buildConfigField("String", "groq_api_key", "\"$groqApiKey\"")
            buildConfigField("String", "gigachat_api_key", "\"$gigachatApiKey\"")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }
}

dependencies {

    // Dagger - Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.navigation.runtime.ktx)
    ksp(libs.dagger.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Media player
    implementation (libs.androidx.media3.exoplayer)
    implementation(libs.common)
    //Markdown to Html
    implementation(libs.markdown)

    // Coil
    implementation(libs.coil.compose)

    // LangChain4j
    implementation(libs.dev.langchain4j.langchain4j.open.ai)
    implementation(libs.langchain4j)

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