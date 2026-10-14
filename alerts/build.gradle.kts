// AlertPipeline (the only path to people), ntfy sending and escalation. JVM only.
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
    api(project(":detectors"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.coroutines.test)
}
