// Pure Kotlin/JVM: an android.* import here does not compile. Holds the models and the ports
// (Clock, HttpClient, OemSource, CheckLog) that :platform and :store implement.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // One shared set of test fakes for every port, used by :detectors and :alerts tests (docs/code-health.md).
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    api(libs.javax.inject)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
