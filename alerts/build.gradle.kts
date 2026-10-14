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

// NetworkVsPrivacyPageTest reads the public privacy page, so an edit to that page alone re-runs the tests (findings #114).
tasks.test {
    inputs.file(rootProject.file("docs/site/privacy.html")).withPropertyName("privacyPage").withPathSensitivity(PathSensitivity.RELATIVE)
}
