plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.peerpath"

    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.peerpath"

        minSdk = 29
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    // ==========================================
    // Android
    // ==========================================

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)


    // ==========================================
    // SUPABASE / HTTP
    // ==========================================

    implementation("com.squareup.okhttp3:okhttp:4.12.0")


    // ==========================================
    // Firebase - temporarily retained
    // ==========================================

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")


    // ==========================================
    // Testing
    // ==========================================

    testImplementation(libs.junit)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}