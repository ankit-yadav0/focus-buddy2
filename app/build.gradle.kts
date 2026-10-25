plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.focussbuddy.kxmpzq"
    minSdk = 24
    targetSdk = 35
    // Overridable from CI via -PappVersionCode=<n> so every published release
    // gets a strictly increasing version code (e.g. the GitHub Actions run
    // number) without needing a manual bump each time. Falls back to 1 for
    // local/manual builds where the property isn't passed.
    versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
    versionName = (project.findProperty("appVersionName") as String?) ?: "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val localDebugKeystore = file("${rootDir}/debug.keystore")
  val releaseKeystore = file("${rootDir}/release.keystore")
  val uploadKeystore = file("${rootDir}/my-upload-key.jks")

  signingConfigs {
    create("release") {
      val customKeystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotEmpty() }
      val store = when {
        customKeystorePath != null -> file(customKeystorePath)
        releaseKeystore.exists() -> releaseKeystore
        uploadKeystore.exists() -> uploadKeystore
        else -> null
      }
      if (store != null) {
        storeFile = store
        storePassword = System.getenv("STORE_PASSWORD")?.takeIf { it.isNotEmpty() }
          ?: System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
          ?: "android"
        keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
          ?: System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
          ?: "android"
      }
    }
    if (localDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = localDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseConfig = signingConfigs.findByName("release")
      if (releaseConfig?.storeFile != null && releaseConfig.storeFile!!.exists()) {
        signingConfig = releaseConfig
      } else if (localDebugKeystore.exists()) {
        signingConfig = signingConfigs.findByName("debugConfig")
      }
    }
    debug {
      if (localDebugKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
