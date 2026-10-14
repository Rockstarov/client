plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Xray core (gomobile AAR, built by 2dust/AndroidLibXrayLite). Pinned on purpose:
// the Kotlin wrapper in core/XrayCore.kt was written against this exact API.
val xrayCoreVersion = "v26.10.10"
val xrayAar = layout.projectDirectory.file("libs/libv2ray.aar").asFile

// Downloaded once at configuration time so that IDE sync and CLI builds both find it.
if (!xrayAar.exists()) {
    xrayAar.parentFile.mkdirs()
    val url = uri("https://github.com/2dust/AndroidLibXrayLite/releases/download/$xrayCoreVersion/libv2ray.aar").toURL()
    logger.lifecycle("Downloading Xray core $xrayCoreVersion (~65 MB) ...")
    val tmp = File(xrayAar.parentFile, "libv2ray.aar.part")
    url.openStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
    tmp.renameTo(xrayAar)
}

android {
    namespace = "net.nexus.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.nexus.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        // Debug keystore is used for the release build too, so the APK is installable out of the box.
        // Replace with your own keystore before publishing anywhere.
        getByName("debug")
    }

    buildTypes {
        release {
            // The gomobile core uses JNI/reflection heavily; keep shrinking off.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
