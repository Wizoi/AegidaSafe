// Android adapters behind the :core ports. The only home of OkHttp, the system clock, maker-specific
// code (oem/) and posted notifications (notify/).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.aegida.platform"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Lint (code-health item 6): only issues not in lint-baseline.xml fail the build.
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = false // `lint` already checks; skips a second pass in assembleRelease
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
}

// Unit tests run once, on the debug variant: no test depends on release, and running both doubled the time (docs/code-health.md).
tasks.withType<Test>().matching { it.name.endsWith("ReleaseUnitTest") }.configureEach { enabled = false }
