plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gandy.wassal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gandy.wassal"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // سر التفعيل بييجي من GitHub Secret اسمه WASSAL_SECRET (مش مكتوب في الكود عشان الريبو)
        val secret = System.getenv("WASSAL_SECRET")?.takeIf { it.isNotBlank() } ?: "LOCAL-DEV-SECRET"
        buildConfigField("String", "LICENSE_SECRET", "\"$secret\"")
    }
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // للتوزيع: اعمل keystore خاص بيك واربطه هنا (signingConfig)
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
