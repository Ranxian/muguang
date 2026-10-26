import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localPropertiesFile = rootProject.file("local.properties")
val releaseSigningProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningFields = listOf("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")

/**
 * 签名参数先看环境变量，再看 local.properties。
 * CI（release workflow）把 jks 落地后用环境变量传进来；本地开发仍然走 local.properties，
 * 两种来源共用同一段校验，缺一项就当没有签名配置、只出未签名的包。
 */
fun signingValue(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: releaseSigningProperties.getProperty(key)?.takeIf { it.isNotBlank() }

val releaseSigningReady = releaseSigningFields.all { signingValue(it) != null } &&
    signingValue("KEYSTORE_FILE")?.let { file(it).isFile } == true

android {
    namespace = "io.github.ranxian.muguang"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ranxian.muguang"
        minSdk = 26
        targetSdk = 36
        // 对外版本号走 1.x（1.0 → 1.1）；历史内部编号不再出现。
        // versionCode 必须继续单调递增（内部试构建用过 1~5），否则装不回已经装了内测版的机器。
        versionCode = 8
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 只打 arm64 的 native 库。
        // ML Kit 的 libbarhopper_v3.so 每个 ABI 各存一份，而且在 APK 里是不压缩存储的，
        // x86/x86_64 两份合计 11.5MB，只有模拟器用得到，真机上永远不会加载。
        // ⚠️ 代价：纯 32 位的老机型装不上（minSdk 26 起这类机器已经很少）。
        ndk {
            abiFilters += "arm64-v8a"
        }

        // 界面只有中文。不限定的话 androidx / material 自带的几十种语言翻译
        // 全都会进 resources.arsc
        resourceConfigurations += listOf("zh", "en")
    }

    val releaseSigningConfig = if (releaseSigningReady) {
        signingConfigs.create("release") {
            storeFile = file(signingValue("KEYSTORE_FILE")!!)
            storePassword = signingValue("KEYSTORE_PASSWORD")
            keyAlias = signingValue("KEY_ALIAS")
            keyPassword = signingValue("KEY_PASSWORD")
        }
    } else {
        logger.warn("未找到完整且有效的 release 签名配置，Release APK 将保持未签名；Debug 与测试任务不受影响。")
        null
    }

    buildTypes {
        release {
            // 本机构建环境（Windows + 受限页面文件）跑 R8 会 OOM；
            // 这是本地验证/自用构建，关闭混淆保证可复现地产出可安装包。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = releaseSigningConfig
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // 单测跑在 JVM 上，android.util.Log 这类框架方法默认会抛
        // "not mocked"；返回默认值让纯逻辑（含日志、JSON 解析）能直接测
        unitTests.isReturnDefaultValues = true
    }
}

// 本机内存紧张（模拟器 + 构建守护进程并行时），测试执行器默认堆会吃掉大块内存
// 曾经直接把 JVM 挤崩（hs_err insufficient memory）。这里给单测限个上限。
tasks.withType<Test>().configureEach {
    maxHeapSize = "512m"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)
    // material-icons-extended 已移除：全项目只用到它里面 2 个图标（扫码、账单），
    // 而 R8 并没有把它裁掉——release 包里实测仍残留 10660 个图标类。
    // 那两个图标已换成项目自己的 vector drawable（ic_qr_code_scanner / ic_receipt_long）。
    // material-icons-core 仍由 material3 传递进来，Icons.Default.* 照常可用。
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.mqtt.paho)
    implementation(libs.mqtt.android)
    implementation(libs.security.crypto)

    // 扫码
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)

    // 支付宝收银台（充值）。AAR 自带 H5PayActivity / PayResultActivity 等清单声明，
    // 合并即用；纯 Java，无 so，对 abiFilters 没影响。
    implementation(libs.alipay.sdk)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
