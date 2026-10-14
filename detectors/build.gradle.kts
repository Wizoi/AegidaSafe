// Pure logic over a Snapshot. JVM only, so no android.* (LayerTest). Only detectors create Findings.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    api(project(":core"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.coroutines.test)
}
