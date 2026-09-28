import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Release signing: keys live outside git. native/keystore.properties wins, otherwise the
// Capacitor project's android/keystore.properties is reused so both builds share one key.
// storeFile is resolved relative to the properties file that defined it.
val keystorePropsFile = listOf(
    rootProject.file("keystore.properties"),
    rootProject.file("../android/keystore.properties"),
).firstOrNull { it.exists() }
val keystoreProps = Properties().apply { keystorePropsFile?.inputStream()?.use { load(it) } }

android {
    namespace = "de.robinrehbein.birdy"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.robinrehbein.birdy"
        minSdk = 24
        targetSdk = 36
        // CI passes the day-based code (see docs/native/spec/platform.md §4.2); local builds must
        // stay above the last Capacitor release (4).
        versionCode = (System.getenv("BIRDY_VERSION_CODE") ?: "5").toInt()
        versionName = "2.0.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile != null) {
                storeFile = keystorePropsFile.parentFile.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropsFile != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing.ktx)
    testImplementation(libs.junit)
}
