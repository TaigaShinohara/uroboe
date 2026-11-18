plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.uroboe"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.uroboe"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.3.10"
    }

    // v0.3.3：署名鍵をプロジェクト内（永続領域）に固定する。
    // 既定のデバッグ鍵は VM 再配置で /root/.android ごと消えて鍵が変わり、
    // 上書きインストール不可→入れ直しでデータが消える事故が起きた（2026-11-15）。
    signingConfigs {
        val ksFile = rootProject.file(".local/uroboe-debug.jks")
        val ksPass = rootProject.file(".local/keystore-password.txt")
            .takeIf { it.exists() }?.readText()?.trim()
        if (ksFile.exists() && !ksPass.isNullOrBlank()) {
            create("stable") {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = "uroboe"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
