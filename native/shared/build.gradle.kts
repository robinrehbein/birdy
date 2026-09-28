import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            // Android + desktop JVM: shares java.nio-backed helpers between the two GL facades.
            group("jvmShared") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Headless desktop target: drives the same engine + GLSL ES shaders through LWJGL
    // for the :screenshots tool and JVM-side tests.
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Declared for the future iOS app; not linkable on Linux hosts. No iosX64: Compose
    // Multiplatform 1.12 dropped Intel simulators.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.io.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.egl)
                implementation(libs.lwjgl.opengles)
            }
        }
        // Desktop tests may render headless through HeadlessEglContext (GL renderer tests).
        val desktopTest by getting {
            dependencies {
                runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-linux")
                runtimeOnly("org.lwjgl:lwjgl-opengles:${libs.versions.lwjgl.get()}:natives-linux")
                // Skia/skiko runtime so UI tests can render Compose headless (ImageComposeScene).
                implementation(libs.compose.desktop.linux.x64)
            }
        }
    }
}

android {
    namespace = "de.robinrehbein.birdy.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "de.robinrehbein.birdy.ui.res"
    generateResClass = always
}

// Golden fixtures live in the repo root (docs/native/golden); tests resolve them relative to this dir.
tasks.withType<Test>().configureEach {
    workingDir = projectDir
    environment("EGL_PLATFORM", "surfaceless")
    systemProperty("birdy.golden.dir", rootDir.parentFile.resolve("docs/native/golden").absolutePath)
}
