plugins {
  id("com.android.application") version "8.5.2"
  id("org.jetbrains.kotlin.android") version "2.0.20"
}

android {
  namespace = "com.orionstar.openclaw"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.orionstar.openclaw"
    // Agent SDK README requires API 26+
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.viewpager2:viewpager2:1.1.0")

  // AgentOS SDK (for TTS/ASR etc.)
  implementation("com.orionstar.agent:sdk:0.4.5-SNAPSHOT")

  // NOTE: AgentOS SDK v0.4.4+ already bundles RobotService/RobotOS deps.
  // Avoid adding robotservice.jar manually, otherwise it causes duplicate classes.

  // WebSocket support
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  // JSON parsing
  implementation("org.json:json:20231013")

  // robotservice.jar depends on Gson
  implementation("com.google.code.gson:gson:2.10.1")
}
