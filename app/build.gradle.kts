plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nexauren.musicplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexauren.musicplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        val paypalClientId = providers.gradleProperty("PAYPAL_CLIENT_ID").orElse("").get()
        val paypalCheckoutUrl = providers.gradleProperty("PAYPAL_CHECKOUT_URL")
            .getOrElse("")
            .trim()
            .ifBlank { "https://example.com/paypal-checkout" }
        val updateManifestUrl = providers.gradleProperty("UPDATE_MANIFEST_URL")
            .getOrElse("")
            .trim()
            .ifBlank { "https://raw.githubusercontent.com/nexauren1/Music-player-/main/update.json" }

        buildConfigField("String", "PAYPAL_CLIENT_ID", "\"$paypalClientId\"")
        buildConfigField("String", "PAYPAL_CHECKOUT_URL", "\"$paypalCheckoutUrl\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
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
}
