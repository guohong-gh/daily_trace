import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// 发布签名参数从仓库根的 keystore.properties 读取（该文件与 keystore 均被 .gitignore 忽略）
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
// 只有真正产出 release 的任务才要求正式签名；单测 / lint / debug 构建不依赖密钥
val needsReleaseSigning = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true) ||
        it.equals("assemble", ignoreCase = true) ||
        it.equals("build", ignoreCase = true) ||
        it.equals("bundle", ignoreCase = true)
}
if (needsReleaseSigning && !keystorePropertiesFile.exists()) {
    throw GradleException(
        "缺少 keystore.properties：release 构建必须使用正式签名，禁止回退调试签名。" +
            "生成与配置方式见 docs/发布流程.md 第五节。"
    )
}

android {
    namespace = "com.gh.daily_trace"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gh.daily_trace"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 正式签名；缺少 keystore.properties 时 configuration 阶段已明确报错，不会回退 debug
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // minSdk 24 需 core library desugaring 才能使用 java.time（API 26+ 原生支持）
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // JDK 25 下 Robolectric 需访问 jdk.internal.access
                test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}

// Room：导出 schema JSON，供后续版本升级编写显式迁移
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // material3 1.4 不再传递依赖图标库，显式引入官方核心图标（回溯箭头/搜索/日历等）
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
