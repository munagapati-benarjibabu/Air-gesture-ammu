import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

val openAiApiKey = localProperties.getProperty("OPENAI_API_KEY", "")
val lostPhoneEmailTo = localProperties.getProperty("LOST_PHONE_EMAIL_TO", "")
val lostPhoneEmailFrom = localProperties.getProperty("LOST_PHONE_EMAIL_FROM", "")
val lostPhoneSmtpHost = localProperties.getProperty("LOST_PHONE_SMTP_HOST", "smtp.gmail.com")
val lostPhoneSmtpPort = localProperties.getProperty("LOST_PHONE_SMTP_PORT", "587")
val lostPhoneSmtpUsername = localProperties.getProperty("LOST_PHONE_SMTP_USERNAME", "")
val lostPhoneSmtpPassword = localProperties.getProperty("LOST_PHONE_SMTP_PASSWORD", "")
val lostModeSecretCode = localProperties.getProperty("LOST_MODE_SECRET_CODE", "ammu123")

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.example.projectammu"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.projectammu"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OPENAI_API_KEY", openAiApiKey.asBuildConfigString())
        buildConfigField("String", "LOST_PHONE_EMAIL_TO", lostPhoneEmailTo.asBuildConfigString())
        buildConfigField("String", "LOST_PHONE_EMAIL_FROM", lostPhoneEmailFrom.asBuildConfigString())
        buildConfigField("String", "LOST_PHONE_SMTP_HOST", lostPhoneSmtpHost.asBuildConfigString())
        buildConfigField("String", "LOST_PHONE_SMTP_PORT", lostPhoneSmtpPort.asBuildConfigString())
        buildConfigField("String", "LOST_PHONE_SMTP_USERNAME", lostPhoneSmtpUsername.asBuildConfigString())
        buildConfigField("String", "LOST_PHONE_SMTP_PASSWORD", lostPhoneSmtpPassword.asBuildConfigString())
        buildConfigField("String", "LOST_MODE_SECRET_CODE", lostModeSecretCode.asBuildConfigString())
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("com.google.mediapipe:tasks-vision:0.10.20")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
