plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "app.uroboe"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.uroboe"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "0.7.3"
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
            // v0.7.2（S-8）：R8 は意図的に無効。端末内完結・外部通信なし・秘密情報なしの小規模アプリで、
            // 難読化の利益より「検証したバイナリそのままを配る」確実さを優先する（APK は約2〜3MBで実害なし）。
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
    // 実機なし検証の標準（2026-11-23）：Robolectric＋Roborazzi で主要画面を JVM 上で検証する
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "1024m"
                // Robolectric のネイティブライブラリ展開先をプロジェクト内に寄せる
                // （この VM の /tmp は 512MB の tmpfs で、他プロジェクトの展開残りで埋まると展開に失敗する）
                it.systemProperty("java.io.tmpdir", rootDir.resolve(".local/tmp").absolutePath)
                // この VM では Java の直通ネットワークが使えないため、Robolectric の
                // ランタイム取得もローカル mirror 経由にする（mirror は build.sh が設定した時だけ有効）
                val mirror = System.getenv("UROBOE_MIRROR")
                if (mirror != null) {
                    it.systemProperty("robolectric.dependency.repo.url", "$mirror/central")
                    it.systemProperty("robolectric.dependency.repo.id", "mirror")
                    it.systemProperty("java.net.preferIPv4Stack", "true")
                }
            }
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
    testImplementation("org.json:json:20240303")
    // 実機なし検証の標準（2026-11-23）
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
}
