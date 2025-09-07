# AdChain SDK Integration Guide for Partners

## 📦 GitHub Packages 통합 가이드

### 1. 사전 준비

#### GitHub Personal Access Token (PAT) 생성
1. GitHub 계정 로그인
2. Settings → Developer settings → Personal access tokens → Tokens (classic)
3. "Generate new token" 클릭
4. 권한 선택:
   - `read:packages` (필수)
   - `repo` (Private 저장소인 경우)
5. 토큰 복사 (한 번만 표시됨)

### 2. 프로젝트 설정

#### 방법 1: gradle.properties 사용 (권장)

**~/.gradle/gradle.properties** 또는 **프로젝트/gradle.properties**:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

**app/build.gradle.kts**:
```kotlin
repositories {
    google()
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/1selfworld-labs/adchain-sdk-android")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: ""
            password = project.findProperty("gpr.token") as String? ?: ""
        }
    }
}

dependencies {
    implementation("com.adchain.sdk:adchain-sdk:1.0.0")
}
```

#### 방법 2: 환경 변수 사용

**환경 변수 설정**:
```bash
export GPR_USER=YOUR_GITHUB_USERNAME
export GPR_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

**app/build.gradle.kts**:
```kotlin
repositories {
    google()
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/1selfworld-labs/adchain-sdk-android")
        credentials {
            username = System.getenv("GPR_USER") ?: ""
            password = System.getenv("GPR_TOKEN") ?: ""
        }
    }
}

dependencies {
    implementation("com.adchain.sdk:adchain-sdk:1.0.0")
}
```

### 3. SDK 초기화

**Application 클래스**:
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = AdchainSdkConfig(
            appKey = "YOUR_APP_KEY",
            appSecret = "YOUR_APP_SECRET",
            environment = AdchainSdkConfig.Environment.PRODUCTION
        )
        
        AdchainSdk.initialize(this, config)
    }
}
```

### 4. 권한 설정

**AndroidManifest.xml**:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

### 5. ProGuard 설정

**proguard-rules.pro**:
```
-keep class com.adchain.sdk.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
```

## 🔒 보안 주의사항

1. **토큰 보안**:
   - PAT를 코드에 직접 하드코딩하지 마세요
   - gradle.properties를 git에 커밋하지 마세요
   - CI/CD에서는 환경 변수나 Secrets 사용

2. **.gitignore 설정**:
```
# Local configuration
local.properties
gradle.properties
```

## 📱 사용 예제

### Mission 기능
```kotlin
// Mission 초기화
val mission = AdchainMission(context)

// 이벤트 리스너 설정
mission.setEventsListener(object : AdchainMissionEventsListener {
    override fun onMissionLoaded(missions: List<Mission>) {
        // 미션 로드 완료
    }
    
    override fun onMissionCompleted(missionId: String) {
        // 미션 완료
    }
    
    override fun onError(error: AdchainAdError) {
        // 에러 처리
    }
})

// 미션 로드
mission.loadMissions()
```

### Quiz 기능
```kotlin
// Quiz 초기화
val quiz = AdchainQuiz(context)

// 이벤트 리스너 설정
quiz.setEventsListener(object : AdchainQuizEventsListener {
    override fun onQuizLoaded(quiz: QuizResponse) {
        // 퀴즈 로드 완료
    }
    
    override fun onQuizCompleted(score: Int) {
        // 퀴즈 완료
    }
    
    override fun onError(error: AdchainAdError) {
        // 에러 처리
    }
})

// 퀴즈 로드
quiz.loadQuiz()
```

### Offerwall 기능
```kotlin
// Offerwall 표시
AdchainSdk.showOfferwall(activity, object : OfferwallCallback {
    override fun onOfferwallOpened() {
        // Offerwall 열림
    }
    
    override fun onOfferwallClosed() {
        // Offerwall 닫힘
    }
    
    override fun onRewardEarned(amount: Int) {
        // 리워드 획득
    }
    
    override fun onError(error: AdchainAdError) {
        // 에러 처리
    }
})
```

## 🆘 문제 해결

### 인증 실패 (401)
- PAT 토큰이 올바른지 확인
- 토큰에 `read:packages` 권한이 있는지 확인
- Private 저장소인 경우 `repo` 권한 필요

### 패키지를 찾을 수 없음
- 저장소 URL이 올바른지 확인
- 최신 버전 번호 확인
- `./gradlew --refresh-dependencies` 실행

### 빌드 실패
- minSdk 24 이상인지 확인
- Kotlin 버전 호환성 확인
- AndroidX 마이그레이션 완료 확인

## 📞 지원

- 이슈: https://github.com/1selfworld-labs/adchain-sdk-android/issues
- 이메일: dev@adchain.com