plugins {
    alias(libs.plugins.android.application)
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
    implementation("org.java-websocket:Java-WebSocket:1.4.0")
    implementation ("com.github.pedroSG94.RootEncoder:library:2.6.4")
    implementation ("com.github.pedroSG94.RootEncoder:extra-sources:2.6.4")
}

