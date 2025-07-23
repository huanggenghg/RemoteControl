plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.mavenPublish)
}

group = "com.lumostech.accessibility"
version = "0.0.1"


afterEvaluate {
     publishing {
       publications {
         // Creates a Maven publication called "release".
         create<MavenPublication>("release") {
           // Applies the component for the release build variant.\
           // from(components["release"])
           // You can then customize attributes of the publication as shown below.
           groupId = (group.toString())
           artifactId = "core"
           version = version
          }
        }
      }
}

android {
    namespace = "com.lumostech.accessibilitycore"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    api(project(":accessibilityBase"))
}