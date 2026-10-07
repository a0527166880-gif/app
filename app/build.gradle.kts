plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

android { namespace="com.a0527166880.offlinelens"; compileSdk=36
 defaultConfig { applicationId="com.a0527166880.offlinelens"; minSdk=26; targetSdk=36; versionCode=8; versionName="1.3.0"
  ndk { abiFilters += listOf("arm64-v8a") }
 }
 buildFeatures { compose=true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 packaging {
  resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  resources.excludes += "native/**"
  resources.excludes += "com/sun/jna/**"
 }
}

dependencies {
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.activity:activity-compose:1.12.1")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
 implementation(platform("androidx.compose:compose-bom:2025.10.00"))
 implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); implementation("androidx.compose.material3:material3"); implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.camera:camera-camera2:1.5.0"); implementation("androidx.camera:camera-lifecycle:1.5.0"); implementation("androidx.camera:camera-view:1.5.0")
 implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
 implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
 implementation("ai.djl:api:0.38.0")
 implementation("ai.djl.huggingface:tokenizers:0.38.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
