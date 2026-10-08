// The only SQL in the app (SqlSingleOwnerTest). Room with exported schemas and explicit migrations;
// a destructive fallback is banned. Every schema change is a one-way choice: ask first (CLAUDE.md).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
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
    testOptions { unitTests.isIncludeAndroidResources = true }
    // MigrationTestHelper reads the exported schema JSONs as assets.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

kapt {
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    api(project(":core"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
}
