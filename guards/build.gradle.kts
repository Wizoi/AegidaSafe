// Guard tests (design section 4): JVM scans of the shipped sources. Each one fails the build.
// Ships nothing; it only has tests. Reads the repo from the aegida.root system property.
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
    testImplementation(project(":core"))
    testImplementation(libs.junit)
}

tasks.test {
    systemProperty("aegida.root", rootDir.absolutePath)
    // Room writes store/schemas during kapt; read it after that in the same build, not in parallel.
    mustRunAfter(":store:kaptDebugKotlin", ":store:kaptReleaseKotlin")
    // The scanned files are inputs, so editing any source re-runs the guards.
    inputs.files(
        fileTree(rootDir) {
            include("*/src/main/**", "*/build.gradle.kts", "store/schemas/**", "*.gradle.kts", "gradle.properties")
            exclude("**/build/**")
        },
    ).withPropertyName("shippedSources").withPathSensitivity(PathSensitivity.RELATIVE)
}
