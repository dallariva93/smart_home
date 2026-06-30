import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Segreti letti da local.properties (NON tracciato da git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "it.casa.clima"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.casa.clima"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"

        // Iniettati da local.properties: nessun segreto nel sorgente tracciato
        buildConfigField("String", "CLIMA_API_KEY", "\"${localProps.getProperty("CLIMA_API_KEY", "")}\"")
        buildConfigField("String", "CLIMA_BASE_URL", "\"${localProps.getProperty("CLIMA_BASE_URL", "https://example.invalid/")}\"")
    }

    buildTypes {
        debug {
            // In debug si consente il traffico in chiaro per testare in LAN su http://IP:8000
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            // R8: offusca e rimuove codice/risorse non usati (APK più piccolo e robusto)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // In release il backend è solo HTTPS: niente traffico in chiaro
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            // Nota: nessun signingConfig — la firma APK si configura a parte con un keystore.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
