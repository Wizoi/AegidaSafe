// Wires and draws. No decisions here (LayerTest): logic goes in :detectors / :alerts.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
}

android {
    namespace = "com.aegida.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aegida.app"
        minSdk = 29 // RoleManager (default-app drift) needs Android 10
        targetSdk = 36 // Play: within one year of the latest Android
        versionCode = 16
        versionName = "0.4.2"
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
    // Lint (code-health item 6): every issue fails the build, warnings included; no baseline (Kevin, Oct 14: fix what is found).
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = false // `lint` already checks; skips a second pass in assembleRelease
        lintConfig = rootProject.file("lint.xml") // only the date-driven checks are relaxed, see lint.xml
    }
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
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(testFixtures(project(":core")))
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

// Keep each release's R8 mapping so a crash line from the support log can be deobfuscated later (retrace).
// release-mappings/ is git-ignored; the bundle also carries the mapping for Play.
val archiveReleaseMapping by tasks.registering(Copy::class) {
    val mappingDir = rootProject.layout.projectDirectory.dir("release-mappings/${android.defaultConfig.versionCode}")
    val archived = mappingDir.file("mapping.txt").asFile
    from(layout.buildDirectory.file("outputs/mapping/release/mapping.txt"))
    into(mappingDir)
    doLast {
        if (!archived.isFile) throw GradleException("R8 mapping was not archived to $archived")
    }
}
tasks.matching { it.name == "minifyReleaseWithR8" }.configureEach {
    finalizedBy(archiveReleaseMapping)
}

// Gradle Play Publisher takes release notes only from release-notes/<locale>/<track>.txt or default.txt, never from
// <versionCode>.txt (findings #83: Play kept showing version 1's "Initial Release"). The notes stay per version as the
// history (release-notes/en-US/<versionCode>.txt, checked by PlayListingTest); this copies the current version's file to
// src/release/play/release-notes/en-US/default.txt (git-ignored) before the plugin builds its upload resources.
val syncReleaseNotes by tasks.registering(Copy::class) {
    from(layout.projectDirectory.file("src/main/play/release-notes/en-US/${android.defaultConfig.versionCode}.txt"))
    into(layout.projectDirectory.dir("src/release/play/release-notes/en-US"))
    rename { "default.txt" }
}
tasks.matching { it.name.startsWith("generate") && it.name.endsWith("PlayResources") }.configureEach { dependsOn(syncReleaseNotes) }

// Gradle Play Publisher. Every task that talks to Play (publishListing, publishReleaseBundle, promoteArtifact)
// needs Kevin's explicit yes first (CLAUDE.md); docs/PUBLISH.md has the commands. The service account key stays
// outside the repo: AEGIDA_PLAY_SERVICE_ACCOUNT in ~/.gradle/gradle.properties names the file (NoSecretsTest scans the
// whole folder, so a key copied in here fails the build). The git-ignored root file is only a fallback name.
play {
    serviceAccountCredentials.set(
        layout.file(providers.gradleProperty("AEGIDA_PLAY_SERVICE_ACCOUNT").map { file(it) }.orElse(rootProject.file("play-service-account.json"))),
    )
    defaultToAppBundles.set(true)
    track.set("internal")
}

// PlayListingTest reads the store listing and this file straight from disk, so Gradle must know a change to
// either one means the tests have to run again (otherwise a listing-only edit would skip them as up to date).
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/play")
    inputs.dir("../docs/site")
    inputs.file("build.gradle.kts")
}

// Unit tests run once, on the debug variant (docs/code-health.md), except ManifestPolicyTest, which also checks the release merged
// manifest: that is the one that ships (findings #35, #105).
tasks.withType<Test>().matching { it.name == "testReleaseUnitTest" }.configureEach { filter { includeTestsMatching("*ManifestPolicyTest") } }
