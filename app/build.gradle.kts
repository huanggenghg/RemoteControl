plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val zegoTokenEndpoint = providers.gradleProperty("zegoTokenEndpoint")
    .orElse(providers.environmentVariable("ZEGO_TOKEN_ENDPOINT"))
    .getOrElse("https://opub.cn/remotecontrol/getZegoToken")
val zegoTokenEndpointLiteral = "\"" + zegoTokenEndpoint.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    namespace = "com.lumostech.remotecontrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumostech.remotecontrol"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "provider"
    productFlavors {
        create("zego") {
            dimension = "provider"
            buildConfigField("String", "ZEGO_TOKEN_ENDPOINT", zegoTokenEndpointLiteral)
        }
        create("agora") { dimension = "provider" }
    }

    buildTypes {
        release {
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
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(project(":communication-api"))
    add("zegoImplementation", project(":communication-zego"))
    add("agoraImplementation", project(":communication-agora"))
    implementation(libs.android.material)
    implementation(libs.libadb.android)
    implementation(libs.conscrypt.android)
//    implementation(libs.accessibilityCore)
    api(project(":accessibilityCore"))

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation("com.squareup.okio:okio:3.6.0")
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
