plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}
android {
    namespace = "com.mission.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mission.launcher"
        minSdk = 30
        targetSdk = 35
        versionCode = 100 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "3.1"
    }
    // One fixed key for every build so a newer APK can be installed over the old one (and in-app update works).
    signingConfigs {
        create("mission") {
            storeFile = file("mission.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mission")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("mission")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    buildFeatures {
        buildConfig = true
    }
}

detekt {
    ignoreFailures = true
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    debugImplementation(libs.leakcanary)
    debugImplementation(libs.metrics)
    implementation(libs.workmanager)
}
