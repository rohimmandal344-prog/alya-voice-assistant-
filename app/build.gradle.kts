import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.ByteArrayOutputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

val gitCommitCountProvider: Provider<Int> = providers.of(GitCommitCountValueSource::class.java) {}
val gitVersionNameProvider: Provider<String> = providers.of(GitVersionNameValueSource::class.java) {}

abstract class GitCommitCountValueSource : ValueSource<Int, ValueSourceParameters.None> {
  override fun obtain(): Int {
    return try {
      val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .redirectErrorStream(true)
        .start()
      val countStr = process.inputStream.bufferedReader().readText().trim()
      val count = countStr.toIntOrNull() ?: 1
      600 + count
    } catch (e: Exception) {
      601
    }
  }
}

abstract class GitVersionNameValueSource : ValueSource<String, ValueSourceParameters.None> {
  override fun obtain(): String {
    return try {
      val process = ProcessBuilder("git", "describe", "--tags", "--always")
        .redirectErrorStream(true)
        .start()
      val tag = process.inputStream.bufferedReader().readText().trim()
      if (tag.isNotBlank()) {
        if (tag.startsWith("v")) tag.substring(1) else tag
      } else {
        "1.1.5-stable"
      }
    } catch (e: Exception) {
      "1.1.5-stable"
    }
  }
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.alya.assistant.kxmpzq"
    minSdk = 24
    targetSdk = 34
    versionCode = gitCommitCountProvider.get()
    versionName = gitVersionNameProvider.get()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      // Enable R8 full-mode obfuscation and resource shrinking for optimized APK size.
      // Global R8 full-mode is enabled via 'android.enableR8.fullMode=true' in gradle.properties.
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
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
  // Configure androidResources to prevent compression of large ML models and binary assets,
  // ensuring high-performance memory-mapped access at runtime.
  androidResources {
    noCompress += listOf("tflite", "onnx", "pb", "vosk", "zip", "fst", "mdl", "am", "lm", "raw", "far", "bin", "dat", "json", "ort", "model", "pt", "pth", "nnet", "tfl")
  }

  @Suppress("DEPRECATION")
  aaptOptions {
    noCompress += listOf("tflite", "onnx", "pb", "vosk", "zip", "fst", "mdl", "am", "lm", "raw", "far", "bin", "dat", "json", "ort", "model", "pt", "pth", "nnet", "tfl")
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation("androidx.datastore:datastore-preferences:1.1.1")
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.google.generativeai)
  implementation(libs.firebase.crashlytics)

  // Supabase Auth & Core
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.android)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)

  // Google Sign-In via Credential Manager & Google ID
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation("xyz.rementia:openwakeword:0.1.3")
  implementation("com.alphacephei:vosk-android:0.3.47")
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
  "ksp"(libs.moshi.kotlin.codegen)
}
