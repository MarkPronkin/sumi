plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
}

val signingDirectory = file(providers.gradleProperty("sumiSigningDir")
    .getOrElse("${System.getProperty("user.home")}/.config/sumi/signing"))
val releaseKeystore = signingDirectory.resolve("sumi-release.p12")
val releasePassword = providers.fileContents(
    layout.projectDirectory.file(signingDirectory.resolve("keystore.pass").absolutePath)
).asText.orNull?.trimEnd('\r', '\n')
val hasReleaseSigner = releaseKeystore.isFile && !releasePassword.isNullOrEmpty()

android {
    namespace = "dev.cinema.crunchyroll"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "dev.cinema.crunchyroll"
        minSdk = 26
        targetSdk = 37
        versionCode = 20
        versionName = "0.2.0-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notClass"] = "dev.cinema.crunchyroll.NativeAccountPlaybackTest,dev.cinema.crunchyroll.MyAnimeListLiveTest"
    }
    if (hasReleaseSigner) {
        val releaseSigner = signingConfigs.create("sumiRelease") {
            storeFile = releaseKeystore
            storeType = "PKCS12"
            keyAlias = "sumi"
            storePassword = releasePassword
            keyPassword = releasePassword
            enableV3Signing = true
        }
        buildTypes.getByName("release").signingConfig = releaseSigner
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

tasks.register("deviceApk") {
    group = "build"
    description = "Build the Sumi release-signed APK for device installs and updates."
    if (hasReleaseSigner) {
        dependsOn("assembleRelease")
    } else {
        doLast {
            throw GradleException("The device APK needs sumi-release.p12 and keystore.pass in the Sumi signing directory. " +
                "Set -PsumiSigningDir=<directory> to select it, or use :app:assembleDebug for development.")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.datastore)
    implementation(libs.tv.material)
    implementation(libs.media3.player)
    implementation(libs.media3.dash)
    implementation(libs.media3.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.effect)
    implementation(libs.coroutines.android)
    implementation(libs.core.splash)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.rules)
}

// Never let a convenience test invocation install/remove debug packages on every attached device.
val androidTestDevice = providers.environmentVariable("ANDROID_SERIAL")
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    val device = androidTestDevice
    doFirst {
        check(!device.orNull.isNullOrBlank()) {
            "Select one test device explicitly with ANDROID_SERIAL, or use scripts/check-android.sh emulator-5554."
        }
    }
}
