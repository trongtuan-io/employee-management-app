plugins {
    alias(libs.plugins.android.application)
<<<<<<< HEAD
    alias(libs.plugins.google.services)
=======
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
}

android {
    namespace = "com.example.employee_management_app"
<<<<<<< HEAD
    compileSdk = 36
=======
    compileSdk {
        version = release(37)
    }
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f

    defaultConfig {
        applicationId = "com.example.employee_management_app"
        minSdk = 24
<<<<<<< HEAD
        targetSdk = 36
=======
        targetSdk = 37
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
<<<<<<< HEAD
            isMinifyEnabled = false
=======
            optimization {
                enable = false
            }
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
<<<<<<< HEAD
    implementation("com.squareup.okhttp3:okhttp:3.14.9")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Firebase Phone Auth (SMS mien phi)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Tai + hien thi anh dai dien
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
=======
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
<<<<<<< HEAD
}
=======
}
>>>>>>> 2328eaa7a34a7d695abecba54db24530ae3bbd2f
