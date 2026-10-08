// Builds and signs the combined feed (decisions.md, build step 1). Runs in the GitHub Action, not on
// the phone; it reads links with the same :core code the phone uses. Ships nothing in the APK.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

application { mainClass.set("com.aegida.feedtool.MainKt") }

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit)
}
