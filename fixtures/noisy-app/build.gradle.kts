// Test fixture only (finding #92): an app that posts notices on command, so the notice ad-spam check can be seen on the emulator
// with a real NotificationListenerService ranking. Never published; installed only by scripts/emulator_noisy_app.py.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.aegida.fixture.noisy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aegida.fixture.noisy"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
