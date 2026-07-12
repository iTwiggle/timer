plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.itwiggle.randomchime"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.itwiggle.randomchime"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    val encodedCloudKey = rootProject.file("cloud-debug.keystore.b64")
    val cloudKeystoreFile = layout.buildDirectory.file("cloud-debug.keystore").get().asFile.apply {
        parentFile.mkdirs()
        if (!exists() && encodedCloudKey.exists()) writeBytes(java.util.Base64.getDecoder().decode(encodedCloudKey.readText().trim()))
    }
    signingConfigs {
        if (cloudKeystoreFile.exists()) {
            create("cloudDebug") {
                storeFile = cloudKeystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (cloudKeystoreFile.exists()) signingConfig = signingConfigs.getByName("cloudDebug")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("androidx.core:core-ktx:1.16.0") }
