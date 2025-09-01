plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rementia.mlkitdetectors"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rementia.mlkitdetectors"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    androidResources {
        noCompress += listOf("tflite")
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // CameraX
    val camerax = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ML Kit: Face Detection (play-services)
    implementation("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")
    // ML Kit: Pose Detection (both base & accurate for compatibility with defaults API)
    implementation("com.google.mlkit:pose-detection:17.0.0")
    implementation("com.google.mlkit:pose-detection-accurate:17.0.0")

    // MediaPipe Tasks: Image Embedder (MobileNetV3)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")


    // Kotlin/AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
}
