import java.time.LocalDate

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.stocktrack.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.stocktrack.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "4.2"
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
    }

    buildTypes {
        release {
            // Compose runs far slower in a debug build. Same key as debug, so it installs over it and keeps the data.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Android has this built in; the unit tests of the web page run without Android.
    testImplementation("org.json:json:20240303")

    // Local database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Scheduled automatic backups
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Google Drive backup (sign-in / permission only; upload uses plain HTTPS)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
