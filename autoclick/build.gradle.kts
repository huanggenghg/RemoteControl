import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Passwords come from an ignored local file or environment, never from source control.
val releaseProperties = Properties().apply {
    val local = project.file("signing.properties")
    if (local.isFile) local.inputStream().use { load(it) }
}
fun releaseValue(property: String, environment: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: releaseProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseStore = releaseValue("storeFile", "AUTOCLICK_KEYSTORE_PATH")
val releaseStorePassword = releaseValue("storePassword", "AUTOCLICK_KEYSTORE_PASSWORD")
val releaseAlias = releaseValue("keyAlias", "AUTOCLICK_KEY_ALIAS")
val releaseKeyPassword = releaseValue("keyPassword", "AUTOCLICK_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword).all { it != null }
require(releaseSigningReady || listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword).all { it == null }) {
    "AutoClick release signing is incomplete. Configure all four AUTOCLICK_* signing variables or autoclick/signing.properties."
}

android {
    namespace = "com.lumostech.autoclick"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumostech.autoclick"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) create("offlineRelease") {
            storeFile = project.file(requireNotNull(releaseStore))
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            // An unconfigured developer build stays unsigned; no debug-key fallback.
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("offlineRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        dataBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
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

//    implementation(libs.accessibilityCore)
    implementation(project(":accessibilityCore"))

    implementation("com.amulyakhare:com.amulyakhare.textdrawable:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    androidTestImplementation("androidx.work:work-testing:2.11.0")
}
