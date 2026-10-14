// The only SQL in the app (SqlSingleOwnerTest). Room with exported schemas and explicit migrations;
// a destructive fallback is banned. Every schema change is a one-way choice: ask first (CLAUDE.md).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aegida.store"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Lint (code-health item 6): every issue fails the build, warnings included; no baseline (Kevin, Oct 14: fix what is found).
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = false // `lint` already checks; skips a second pass in assembleRelease
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // MigrationTestHelper reads the exported schema JSONs as assets.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
}

// Unit tests run once, on the debug variant: no test depends on release, and running both doubled the time (docs/code-health.md).
tasks.withType<Test>().matching { it.name.endsWith("ReleaseUnitTest") }.configureEach { enabled = false }
