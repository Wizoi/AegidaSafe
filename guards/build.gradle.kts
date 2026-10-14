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
    // Room writes store/schemas during KSP; read it after that in the same build, not in parallel.
    mustRunAfter(":store:kspDebugKotlin", ":store:kspReleaseKotlin")
    // Inputs are every file the guards read (ShippedSources.allText is the widest: the tracked files plus everything under
    // */src), so editing a doc or script re-runs the secrets scan too (findings #9). Agents' scratch copies are not inputs.
    inputs.files(
        fileTree(rootDir) {
            // release-mappings/ is a git-ignored copy of each release's R8 mapping, written by :app:archiveReleaseMapping.
            exclude("**/build/**", "**/.gradle/**", ".git/**", "**/.idea/**", "**/.kotlin/**", "release-mappings/**", ".claude/**")
        },
    ).withPropertyName("shippedSources").withPathSensitivity(PathSensitivity.RELATIVE)
}
