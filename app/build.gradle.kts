plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
}

android {
    compileSdk = 35
    namespace = "com.kooritea.fcmfix"

    defaultConfig {
        applicationId = "com.kooritea.fcmfix"
        minSdk = 29
        targetSdk = 35
        versionCode = 46
        versionName = "dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(fileTree("libs"))

    compileOnly(libs.xposed.api)
    implementation(libs.material)
}
