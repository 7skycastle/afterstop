plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// debug 빌드에서만 평문 HTTP를 허용할 주소. 에뮬레이터의 호스트 PC 별칭(10.0.2.2)과
// 기기 자신의 loopback, 그리고 -PafterstopDevHost 로 지정한 PC LAN IP 하나만 허용한다.
val devHost = (project.findProperty("afterstopDevHost") as String?)?.trim().orEmpty()
require(devHost.isEmpty() || Regex("^[A-Za-z0-9.-]{1,253}$").matches(devHost)) {
    "afterstopDevHost 값이 올바르지 않습니다: $devHost"
}
val generatedDebugRes = layout.buildDirectory.dir("generated/afterstop/debugRes")
val generateDebugNetworkConfig by tasks.registering {
    val out = generatedDebugRes
    val host = devHost
    inputs.property("devHost", host)
    outputs.dir(out)
    doLast {
        val dir = out.get().dir("xml").asFile
        dir.mkdirs()
        val extra = if (host.isNotEmpty()) "\n        <domain includeSubdomains=\"false\">$host</domain>" else ""
        dir.resolve("network_security_config.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!-- 자동 생성 파일(debug 전용). 수정하려면 -PafterstopDevHost 를 사용하세요. -->
            |<network-security-config>
            |    <base-config cleartextTrafficPermitted="false" />
            |    <domain-config cleartextTrafficPermitted="true">
            |        <domain includeSubdomains="false">10.0.2.2</domain>
            |        <domain includeSubdomains="false">127.0.0.1</domain>
            |        <domain includeSubdomains="false">localhost</domain>$extra
            |    </domain-config>
            |</network-security-config>
            """.trimMargin()
        )
    }
}

android {
    namespace = "com.afterstop.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.afterstop.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEV_HOST", "\"$devHost\"")
    }

    // release 서명: 저장소 밖 키 파일을 gradle 속성(afterstopKeystore 등)이나 환경 변수로만 받는다.
    val ks = (findProperty("afterstopKeystore") as String?) ?: System.getenv("AFTERSTOP_KEYSTORE")
    val ksPass = (findProperty("afterstopKeystorePassword") as String?) ?: System.getenv("AFTERSTOP_KEYSTORE_PASSWORD")
    val ksAlias = (findProperty("afterstopKeyAlias") as String?) ?: System.getenv("AFTERSTOP_KEY_ALIAS") ?: "afterstop"
    signingConfigs {
        if (!ks.isNullOrBlank() && !ksPass.isNullOrBlank()) {
            create("release") {
                storeFile = file(ks)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:8787\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets {
        getByName("debug").res.srcDir(generatedDebugRes)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.matching { it.name.startsWith("merge") && it.name.contains("DebugResources") }
    .configureEach { dependsOn(generateDebugNetworkConfig) }
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(generateDebugNetworkConfig) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")

    val camerax = "1.5.3"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
}
