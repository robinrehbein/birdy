plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    application
}

kotlin {
    jvmToolchain(21)
}

val lwjglNatives = "natives-linux"

dependencies {
    implementation(project(":shared"))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.egl)
    implementation(libs.lwjgl.opengles)
    runtimeOnly(variantOf(libs.lwjgl.core) { classifier(lwjglNatives) })
    runtimeOnly(variantOf(libs.lwjgl.opengles) { classifier(lwjglNatives) })
    // Skia/skiko runtime for rendering Compose overlays headless (ImageComposeScene).
    implementation(libs.compose.desktop.linux.x64)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.kotlin.test)
}

application {
    mainClass.set("de.robinrehbein.birdy.shots.MainKt")
}

tasks.named<JavaExec>("run") {
    val outDir = rootProject.layout.buildDirectory.dir("shots")
    outputs.upToDateWhen { false }
    workingDir = rootDir
    environment("EGL_PLATFORM", "surfaceless")
    systemProperty("java.awt.headless", "true")
    args(outDir.get().asFile.absolutePath)
}
