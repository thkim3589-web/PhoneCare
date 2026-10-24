plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.phonecare.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phonecare.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 27
        versionName = "4.2"
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

    // 배포 경로 두 가지: direct = 직접 설치(앱 안 자동 업데이트), store = 플레이 스토어용(자동 업데이트·일부 권한 제외)
    flavorDimensions += "dist"
    productFlavors {
        create("direct") {
            dimension = "dist"
            buildConfigField("boolean", "STORE", "false")
        }
        create("store") {
            dimension = "dist"
            buildConfigField("boolean", "STORE", "true")
        }
    }

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
    // 동영상 압축 (구글 공식 Media3 변환 라이브러리)
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
}
