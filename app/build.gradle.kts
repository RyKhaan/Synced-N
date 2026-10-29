import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val envProps = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
    val dotEnvFile = rootProject.file(".env")
    if (dotEnvFile.exists()) {
        dotEnvFile.inputStream().use { load(it) }
    }
}

fun resolveEnvSecret(key: String, defaultVal: String = ""): String {
    return envProps.getProperty(key)?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: defaultVal
}

android {
    namespace = "com.example.syncedn"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.syncedn"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val brevoKey = resolveEnvSecret("BREVO_API_KEY")
        val brevoSender = resolveEnvSecret("BREVO_SENDER_EMAIL", "ryanlloydgenturo@gmail.com")
        val cloudName = resolveEnvSecret("CLOUDINARY_CLOUD_NAME", "ffb3fdkm")
        val uploadPreset = resolveEnvSecret("CLOUDINARY_UPLOAD_PRESET", "synced_preset")

        buildConfigField("String", "BREVO_API_KEY", "\"$brevoKey\"")
        buildConfigField("String", "BREVO_SENDER_EMAIL", "\"$brevoSender\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$uploadPreset\"")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.browser)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
