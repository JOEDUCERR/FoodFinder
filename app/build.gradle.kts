plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    // UNCOMMENT the following line when you connect Firebase:
    // alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.foodfinderfinal1"
    compileSdk = 36 // "release(36)" syntax from user, changing to standard integer to ensure it builds 
                    // (User's snippet had compileSdk { version = release(36) }, sticking to standard)

    defaultConfig {
        applicationId = "com.example.foodfinderfinal1"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Firebase (BOM is recommended for managing versions)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Coroutines for networking
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
