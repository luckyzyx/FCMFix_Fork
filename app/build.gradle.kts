import java.io.FileInputStream
import java.util.Properties

val isBuildCI = gradle.startParameter.taskNames.any { it == "buildCI" }

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = 35
    namespace = "com.luckyzyx.fcmfix"

    defaultConfig {
        applicationId = "com.luckyzyx.fcmfix"
        minSdk = 29
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = getVersionCode()
        versionName = if (isBuildCI) "ci" else "dev"
        ndk.abiFilters.add("arm64-v8a")
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
    applicationVariants.all {
        val buildType = buildType.name
        val version = "${versionName}-${versionCode}"
        println("buildVersion -> $version ($buildType)")
        outputs.all {
            @Suppress("DEPRECATION")
            if (this is com.android.build.gradle.api.ApkVariantOutput) {
                if (buildType == "release") outputFileName = "FCMFix-${version}.apk"
                if (buildType == "debug") outputFileName = "FCMFix-${version}_debug.apk"
                println("outputFileName -> $outputFileName")
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "META-INF/**"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "**.properties"
            excludes += "**.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

tasks.register("buildCI") {
    group = "ci"
    dependsOn("assembleRelease")
}

dependencies {
    implementation(fileTree("libs"))

    compileOnly(libs.xposed.api)
    if (isBuildCI) implementation(libs.yukihookapi)
    else implementation(libs.yukihookapi.local)

    ksp(libs.ksp.yukihookapi)
    implementation(libs.dexkit)

    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.preference.ktx)
    implementation(libs.swiperefreshlayout)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.fastscroll)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.net)
    implementation(libs.okhttp)

}

fun getVersionCode(): Int {
    val propsFile = file("version.properties")
    if (propsFile.canRead()) {
        val properties = Properties()
        properties.load(FileInputStream(propsFile))
        var vCode = properties["versionCode"].toString().toInt()
        properties["versionCode"] = (++vCode).toString()
        properties.store(propsFile.writer(), null)
        println("versionCode -> $vCode")
        return vCode
    } else throw GradleException("Can't read version.properties!")
}