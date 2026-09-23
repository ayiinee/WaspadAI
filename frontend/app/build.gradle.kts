import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    listOf(
        rootProject.file("../.env"),
        rootProject.file("local.properties"),
    ).forEach { file ->
        if (file.exists()) {
            file.inputStream().use { input -> load(input) }
        }
    }
}

fun publicConfig(name: String, defaultValue: String = ""): String {
    return (localProperties.getProperty(name) ?: System.getenv(name) ?: defaultValue).trim()
}

fun buildConfigString(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "id.waspadai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.waspadai.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "WASPADAI_API_BASE_URL",
            buildConfigString(publicConfig("WASPADAI_API_BASE_URL", "http://10.0.2.2:8001"))
        )
        buildConfigField("boolean", "WASPADAI_REMOTE_ENABLED", publicConfig("WASPADAI_REMOTE_ENABLED", "true"))
        buildConfigField(
            "String",
            "WASPADAI_SUPABASE_ACCESS_TOKEN",
            buildConfigString(publicConfig("WASPADAI_SUPABASE_ACCESS_TOKEN"))
        )
        buildConfigField(
            "String",
            "WASPADAI_SUPABASE_URL",
            buildConfigString(publicConfig("WASPADAI_SUPABASE_URL", publicConfig("SUPABASE_URL")))
        )
        buildConfigField(
            "String",
            "WASPADAI_SUPABASE_PUBLISHABLE_KEY",
            buildConfigString(
                publicConfig(
                    "WASPADAI_SUPABASE_PUBLISHABLE_KEY",
                    publicConfig("SUPABASE_PUBLISHABLE_KEY")
                )
            )
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
