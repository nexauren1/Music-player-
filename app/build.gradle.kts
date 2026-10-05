plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.musicplayer.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.musicplayer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.5.1"

        val paypalClientId = providers.gradleProperty("PAYPAL_CLIENT_ID").orElse("").get()
        val paypalQuarterlyUrl = providers.gradleProperty("PAYPAL_QUARTERLY_URL")
            .getOrElse("")
            .trim()
        val paypalLifetimeUrl = providers.gradleProperty("PAYPAL_LIFETIME_URL")
            .getOrElse("")
            .trim()
        val paypalWorkerUrl = providers.gradleProperty("PAYPAL_WORKER_URL")
            .getOrElse("https://icy-bread-c703.nexaurenstore.workers.dev")
            .trim()
        val updateManifestUrl = providers.gradleProperty("UPDATE_MANIFEST_URL")
            .getOrElse("")
            .trim()
            .ifBlank { "https://raw.githubusercontent.com/nexauren1/Music-player-/main/update.json" }

        buildConfigField("String", "PAYPAL_CLIENT_ID", "\"$paypalClientId\"")
        buildConfigField("String", "PAYPAL_QUARTERLY_URL", "\"$paypalQuarterlyUrl\"")
        buildConfigField("String", "PAYPAL_LIFETIME_URL", "\"$paypalLifetimeUrl\"")
        buildConfigField("String", "PAYPAL_WORKER_URL", "\"$paypalWorkerUrl\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"" )
    }

    val releaseStoreFile = System.getenv("KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("KEY_ALIAS")
    val releaseKeyPassword = System.getenv("KEY_PASSWORD")
    val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-session:1.10.0")
    implementation("androidx.media3:media3-common:1.10.0")

    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")

    // Firebase account, premium entitlement and Google Sign-In stack.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
