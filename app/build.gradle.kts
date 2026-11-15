import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val firebaseConfigured = file("google-services.json").exists()
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val betaVersionName = System.getenv("GITHUB_REF_NAME")?.removePrefix("beta-")
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.chvertko.focusstep"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chvertko.focusstep"
        minSdk = 26
        targetSdk = 36
        versionCode = ciRunNumber?.coerceAtLeast(1) ?: 1
        versionName = betaVersionName?.takeIf { it.isNotBlank() } ?: "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val betaKeystorePath = System.getenv("BETA_KEYSTORE_PATH")
    if (!betaKeystorePath.isNullOrBlank()) {
        signingConfigs {
            create("beta") {
                storeFile = file(betaKeystorePath)
                storePassword = System.getenv("BETA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BETA_KEY_ALIAS")
                keyPassword = System.getenv("BETA_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    buildTypes {
        getByName("release") {
            if (!betaKeystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("beta")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
}
