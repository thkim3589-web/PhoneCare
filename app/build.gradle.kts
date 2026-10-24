plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.phonecare.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phonecare.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "3.8"
        // GitHub 빌드 번호 (자동 업데이트 비교용, 직접 빌드하면 0)
        buildConfigField("int", "BUILD_NUMBER", System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // 정식 서명 키: GitHub Secrets에 키가 등록돼 있으면 정식 키, 없으면 테스트 키로 서명
    val releaseStore = System.getenv("RELEASE_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStore != null) {
            create("official") {
                storeFile = releaseStore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = "phonecare"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    val signKey = if (releaseStore != null) signingConfigs.getByName("official") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = signKey
        }
        release {
            isMinifyEnabled = false
            signingConfig = signKey
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
    // 코드 검사(Lint): 문제가 있어도 빌드는 멈추지 않고 결과만 파일로 남김
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        textReport = true
        textOutput = file("build/lint-results.txt")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
