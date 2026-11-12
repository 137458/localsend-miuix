plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    // 样式违规会导致构建失败，可执行 ./gradlew ktlintFormat 自动修复
    ignoreFailures.set(false)
}

android {
    namespace = "org.localsend.miuix"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.localsend.miuix.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 12401
        versionName = "1.24.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/io.netty.versions.properties",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE",
                    "META-INF/LICENSE.txt",
                    "META-INF/license.txt",
                    "META-INF/NOTICE",
                    "META-INF/NOTICE.txt",
                    "META-INF/notice.txt",
                    "META-INF/*.version",
                    "META-INF/native-image/**",
                    "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration",
                    "org/fusesource/jansi/**",
                    "org/bouncycastle/pqc/**",
                )
        }
    }
}

dependencies {
    // coUI-Kit 0.0.1-alpha10 runtime 传递要求 core-ktx:1.19.1，显式对齐以避免编译/运行版本分裂
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Provides LocalNavigationEventDispatcherOwner needed by Miuix WindowDialog/NavigationBackHandler
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

    // Jetpack Compose: Miuix 0.9.4 依赖 foundation:1.12.0，coUI 0.0.1-alpha10 强依赖 compose-bom-alpha:2026.08.01
    // (将 ui/foundation 抬升至 1.13.0-alpha02，同时传递 kotlin-stdlib:2.4.20)。显式锁定同一 BOM 以消除隐式漂移，不可回钉至 1.7.6。
    val composeBom = platform("androidx.compose:compose-bom-alpha:2026.08.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-util")
    implementation("androidx.compose.foundation:foundation")

    // Miuix (HyperOS Compose UI Library, Nav & Blur Engine)
    val miuixVersion = "0.9.4"
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:$miuixVersion")

    // coUI-Kit (ColorOS Compose 皮肤方案；AAR 强制 minSdk 31，且强依赖 compose-bom-alpha:2026.08.01 与 foundation:1.12.0+)
    implementation("io.github.itoseo:coui:0.0.1-alpha11")

    // Kotlinx Coroutines & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ktor Server & Client for LocalSend HTTP REST API & File Streaming
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    // Netty 服务端引擎：CIO 服务端不支持 TLS，HTTPS 模式需切换 Netty
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    // BouncyCastle：用于在运行时生成自签名证书（HTTPS）
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // ZXing for compliant, fast and scan-friendly QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
}
