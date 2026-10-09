// Wires and draws. No decisions here (LayerTest): logic goes in :detectors / :alerts.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.aegida.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aegida.app"
        minSdk = 29 // RoleManager (default-app drift) needs Android 10
        targetSdk = 36 // Play: within one year of the latest Android
        versionCode = 1
        versionName = "0.1.0"
        // Device-test builds report -local so they cannot be mistaken for a Play build.
        if (project.hasProperty("localBuild")) versionNameSuffix = "-local"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Upload key for Play App Signing. Values come from ~/.gradle/gradle.properties or -P flags, never the repo.
    // Without them the release build is simply unsigned (tests and local builds do not need the key).
    val uploadStoreFile = providers.gradleProperty("AEGIDA_UPLOAD_STORE_FILE").orNull
    if (uploadStoreFile != null) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = providers.gradleProperty("AEGIDA_UPLOAD_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("AEGIDA_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("AEGIDA_UPLOAD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true // Robolectric reads the merged manifest (ManifestPolicyTest)
        }
    }
}

dependencies {
    implementation(project(":alerts"))
    implementation(project(":platform"))
    implementation(project(":store"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
