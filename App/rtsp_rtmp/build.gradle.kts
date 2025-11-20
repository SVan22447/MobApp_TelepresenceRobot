plugins {
    alias(libs.plugins.android.application)
    //noinspection NewerVersionAvailable
    id ("com.google.secrets_gradle_plugin") version "0.6.1"
}

android {
    namespace = "com.example.telepresencerobot"
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        applicationId = "com.example.telepresencerobot"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.mediarouter)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("org.slf4j:slf4j-android:1.7.36")
    implementation("com.github.codecrunchers-x:WebRTC-Android-Library:v1.0.32006")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.mik3y:usb-serial-for-android:3.9.0")
}

