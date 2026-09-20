plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)

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
// value verbatim as BuildConfig source. These three keys are shared's own
// (com.example.shared.BuildConfig via manual Properties parsing, which adds
// its own quoting) -- letting this plugin also auto-generate a same-named,
// unused field here from the same unquoted local.properties value produces
// invalid Java whenever the value is empty (e.g. an unset CI secret).
secrets {
    ignoreList.add("gemini_api_key")
    ignoreList.add("grok_api_key")
    ignoreList.add("gigachat_api_key")
}

android {
    namespace = "com.oneclicktrip"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                //"proguard-rules.pro"
            )
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

    // Room Specific
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
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,INDEX.LIST}"
        }
    }

    configurations.all {
        // can be removed if we upgrade the API back to be >= 26
        // otherwise we will not be able to build a bundle due to
        // limitations or compatibility issues with the specific library (opentelemetry)
        exclude(group = "io.opentelemetry")
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
}

dependencies {

    // Desugaring for ensuring stable app's work for older api.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Google Cloud Storage
    //implementation(libs.google.cloud.storage)

    // Google cloud vision
    implementation("com.google.apis:google-api-services-vision:v1-rev20240823-2.0.0") {
        exclude(group = "com.google.guava", module = "guava-jdk5")
    }
    implementation("com.google.http-client:google-http-client-gson:1.45.0") {
        exclude(group = "com.google.guava", module = "guava-jdk5")
    }

    implementation(libs.androidx.media3.exoplayer)

    // LangChain4j
    implementation(libs.dev.langchain4j.langchain4j.open.ai)
    implementation(libs.langchain4j)

    //Markdown to Html
    implementation(libs.markdown)

    // AppMetrica SDK.
    implementation(libs.analytics)

    // AdMob advertisement
    implementation(libs.play.services.ads)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Logging
    implementation(libs.timber)

    // Coil
    implementation(libs.coil.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.espresso.core)
    implementation(libs.play.services.ads.lite)
    implementation(libs.firebase.appdistribution.gradle)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Dagger - Hilt
    implementation(libs.hilt.android)
    ksp(libs.dagger.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    // Ktor & kotlin Serialization
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.logging.jvm)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // ViewModel utilities for Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Lifecycle utilities for Compose
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Saved state module for ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}