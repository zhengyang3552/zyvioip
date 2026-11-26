plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.zyvioip.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zyvioip.app"
        minSdk = 26
        targetSdk = 34
        // versionCode 由 CI 注入（BUILD_NUMBER），保证单调递增、不会因降级装不上；
        // 本地构建回退到基础值，仅供调试。
        val ciBuild = (System.getenv("BUILD_NUMBER") ?: "").trim()
        versionCode = if (ciBuild.matches(Regex("\\d+"))) 100 + ciBuild.toInt() else 100
        // versionName：tag 构建用 tag 名（v0.0.1 -> 0.0.1），其余回退到默认值。
        val ciName = (System.getenv("VERSION_NAME") ?: "").trim().removePrefix("v")
        versionName = ciName.ifBlank { "0.0.1" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            val ksPassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasName = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")
            // 没有注入密钥时（本地直接构建），不要建立“半个”签名配置，
            // 否则后面的 signingConfig 赋值会让构建直接失败。
            if (!ksPath.isNullOrBlank() && !ksPassword.isNullOrBlank() &&
                !keyAliasName.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                storeFile = rootProject.file(ksPath)
                storePassword = ksPassword
                keyAlias = keyAliasName
                this.keyPassword = keyPassword
                // 三种签名方案全开。AGP 在 minSdk >= 24 时默认只签 v2，
                // 而 MIUI/HyperOS、ColorOS 等国产 ROM 的包安装器仍会读 v1（JAR）签名做预校验，
                // 只有 v2 的包会被判成“解析软件包时出现问题 / 无效的安装包”。
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    // KEYSTORE_PATH 存在时才算签名可用
    val hasReleaseSigning = !System.getenv("KEYSTORE_PATH").isNullOrBlank()

    buildTypes {
        // debug / release 共用同一个固定签名，两者可互相覆盖安装，也不会出现签名冲突。
        // 未注入密钥时保持 AGP 默认（debug 用调试签名、release 不签名），本地构建不受影响。
        getByName("debug") {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        getByName("release") {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")

    // ---- View 系统（XML + RecyclerView）----
    // 弃用 Compose：本项目的数据刷新模式（每次查询整体替换 List）
    // 在 Compose 下极难保证「内容未变就不重组」，反复出现持续掉帧。
    // RecyclerView + DiffUtil 由框架强制做差异比对，同样数据不会重绘。
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    /**
     * 全部 lifecycle 组件固定 2.7.0。
     *
     * androidx.lifecycle 是 **atomic group**：组内任意一个包为 2.8.1 时，
     * Gradle 会把整组统一到 2.8.1 —— 单独把 lifecycle-runtime-compose 写成
     * 2.7.0 是无效的（v0.0.6 就是这么失败的，APK 里实际仍是 2.8.1）。
     *
     * 为什么不能用 2.8.x：2.8.0 起 lifecycle-runtime-compose 把
     * LocalLifecycleOwner 迁到了 androidx.lifecycle.compose 包下，
     * 需要 compose-ui >= 1.7 才会 Provider 它；本项目 BOM 2024.06 的
     * compose-ui 是 1.6.8，于是启动即崩：
     *   IllegalStateException: CompositionLocal LocalLifecycleOwner not present
     *
     * 2.7.0 的 collectAsStateWithLifecycle 走的是 compose-ui 自带的
     * androidx.compose.ui.platform.LocalLifecycleOwner，与 1.6.8 匹配。
     * 升级 Compose BOM 到 2024.09+ 之后，这里才能一起升到 2.8.x。
     */
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
