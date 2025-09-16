# AdChain SDK for Android

[![](https://jitpack.io/v/1selfworld-labs/adchain-sdk-android.svg)](https://jitpack.io/#1selfworld-labs/adchain-sdk-android)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

AdChain SDK는 Android 앱에 리워드 광고를 쉽게 통합할 수 있는 강력한 SDK입니다. Offerwall, Mission, Quiz, Banner 등 다양한 광고 형식을 지원합니다.

## 📋 목차

- [주요 기능](#주요-기능)
- [요구사항](#요구사항)
- [설치](#설치)
- [시작하기](#시작하기)
- [API 가이드](#api-가이드)
- [고급 기능](#고급-기능)
- [ProGuard 설정](#proguard-설정)
- [문제 해결](#문제-해결)
- [라이센스](#라이센스)

## 🚀 주요 기능

- **Offerwall**: 사용자가 다양한 광고를 보고 리워드를 받을 수 있는 오퍼월
- **Mission System**: 특정 작업 완료 시 리워드를 제공하는 미션 시스템
- **Quiz**: 퀴즈 참여를 통한 리워드 획득
- **Banner**: 배너 광고 표시
- **자동 사용자 관리**: 세션 관리 및 자동 로그인
- **실시간 이벤트 추적**: 광고 노출, 클릭, 완료 추적

## 📱 요구사항

- Android API 레벨 24 (Android 7.0) 이상
- Kotlin 1.9.0 이상
- AndroidX 라이브러리

## 🔧 설치

### Step 1: JitPack 저장소 추가

프로젝트 레벨 `settings.gradle.kts` (또는 `settings.gradle`) 파일에 JitPack 저장소를 추가합니다:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: SDK 의존성 추가

앱 레벨 `build.gradle.kts` (또는 `build.gradle`) 파일에 SDK 의존성을 추가합니다:

```kotlin
dependencies {
    implementation("com.github.1selfworld-labs:adchain-sdk-android:v1.0.10")
}
```

### Step 3: 권한 설정

`AndroidManifest.xml`에 필요한 권한을 추가합니다:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Google Advertising ID 사용을 위한 권한 -->
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

## 🎯 시작하기

### 1. SDK 초기화

Application 클래스에서 SDK를 초기화합니다:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // SDK 설정
        val config = AdchainSdkConfig.Builder()
            .appKey("YOUR_APP_KEY")
            .appSecret("YOUR_APP_SECRET")
            .isDebugMode(BuildConfig.DEBUG)
            .build()

        // SDK 초기화
        AdchainSdk.initialize(this, config)
    }
}
```

### 2. 사용자 로그인

SDK 기능을 사용하기 전에 사용자 로그인이 필요합니다:

```kotlin
// 사용자 정보 생성
val user = AdchainSdkUser(
    userId = "unique_user_id",
    gender = AdchainSdkUser.Gender.MALE, // 또는 FEMALE, OTHER
    birthYear = 1990
)

// 로그인
AdchainSdk.login(user, object : AdchainSdkLoginListener {
    override fun onSuccess() {
        Log.d("AdChain", "Login successful")
        // 이제 SDK 기능을 사용할 수 있습니다
    }

    override fun onFailure(errorType: AdchainSdkLoginListener.ErrorType) {
        Log.e("AdChain", "Login failed: $errorType")
    }
})
```

### 3. Offerwall 표시

```kotlin
// Offerwall 표시
AdchainSdk.openOfferwall(activity, object : OfferwallCallback {
    override fun onOpened() {
        Log.d("AdChain", "Offerwall opened")
    }

    override fun onClosed() {
        Log.d("AdChain", "Offerwall closed")
    }

    override fun onFailed(error: String) {
        Log.e("AdChain", "Offerwall failed: $error")
    }

    override fun onRewardReceived(reward: String) {
        Log.d("AdChain", "Reward received: $reward")
    }
})
```

## 📖 API 가이드

### Mission API

미션 목록을 가져오고 완료 처리:

```kotlin
// Mission 인스턴스 생성
val mission = AdchainMission("mission_unit_id")

// 이벤트 리스너 설정 (선택사항)
mission.setEventsListener(object : AdchainMissionEventsListener {
    override fun onMissionListLoaded(missions: List<Mission>) {
        Log.d("Mission", "Loaded ${missions.size} missions")
    }

    override fun onMissionStarted(mission: Mission) {
        Log.d("Mission", "Started: ${mission.missionName}")
    }

    override fun onMissionCompleted(mission: Mission, rewardUrl: String?) {
        Log.d("Mission", "Completed: ${mission.missionName}")
        rewardUrl?.let {
            // 리워드 URL 처리
        }
    }

    override fun onError(error: AdchainAdError) {
        Log.e("Mission", "Error: $error")
    }
})

// 미션 목록 가져오기
mission.getMissionList(
    onSuccess = { missions ->
        missions.forEach { mission ->
            Log.d("Mission", "ID: ${mission.missionId}, Name: ${mission.missionName}")
            Log.d("Mission", "Reward: ${mission.rewardAmount}")
        }
    },
    onFailure = { error ->
        Log.e("Mission", "Failed to load missions: $error")
    }
)

// 미션 시작
mission.startMission(
    missionId = "mission_001",
    onSuccess = {
        Log.d("Mission", "Mission started successfully")
    },
    onFailure = { error ->
        Log.e("Mission", "Failed to start mission: $error")
    }
)

// 미션 완료
mission.completeMission(
    missionId = "mission_001",
    onSuccess = { rewardUrl ->
        Log.d("Mission", "Mission completed! Reward URL: $rewardUrl")
    },
    onFailure = { error ->
        Log.e("Mission", "Failed to complete mission: $error")
    }
)
```

### Quiz API

퀴즈 목록을 가져오고 참여:

```kotlin
// Quiz 인스턴스 생성
val quiz = AdchainQuiz("quiz_unit_id")

// 이벤트 리스너 설정 (선택사항)
quiz.setQuizEventsListener(object : AdchainQuizEventsListener {
    override fun onQuizListLoaded(quizEvents: List<QuizEvent>) {
        Log.d("Quiz", "Loaded ${quizEvents.size} quiz events")
    }

    override fun onQuizStarted(quizEvent: QuizEvent) {
        Log.d("Quiz", "Started: ${quizEvent.title}")
    }

    override fun onQuizCompleted(quizEvent: QuizEvent, rewardUrl: String?) {
        Log.d("Quiz", "Completed: ${quizEvent.title}")
    }

    override fun onError(error: AdchainAdError) {
        Log.e("Quiz", "Error: $error")
    }
})

// 퀴즈 목록 가져오기
quiz.getQuizList(
    onSuccess = { quizEvents ->
        quizEvents.forEach { event ->
            Log.d("Quiz", "ID: ${event.quizId}, Title: ${event.title}")
            Log.d("Quiz", "Status: ${event.status}, Reward: ${event.rewardAmount}")
        }
    },
    onFailure = { error ->
        Log.e("Quiz", "Failed to load quiz: $error")
    }
)

// 퀴즈 참여
quiz.participateInQuiz(
    quizId = "quiz_001",
    onSuccess = {
        Log.d("Quiz", "Quiz participation started")
    },
    onFailure = { error ->
        Log.e("Quiz", "Failed to participate: $error")
    }
)
```

### Banner API

배너 광고 가져오기:

```kotlin
AdchainBanner.getBanner(
    placementId = "banner_placement_id",
    onSuccess = { bannerResponse ->
        // 배너 데이터 처리
        Log.d("Banner", "Banner URL: ${bannerResponse.imageUrl}")
        Log.d("Banner", "Click URL: ${bannerResponse.clickUrl}")

        // ImageView에 배너 표시 (예: Glide 사용)
        // Glide.with(context)
        //     .load(bannerResponse.imageUrl)
        //     .into(bannerImageView)
    },
    onFailure = { error ->
        Log.e("Banner", "Failed to load banner: $error")
    }
)
```

## 🔧 고급 기능

### 사용자 상태 확인

```kotlin
// 로그인 상태 확인
if (AdchainSdk.isLoggedIn) {
    // 사용자가 로그인됨
    val currentUser = AdchainSdk.getCurrentUser()
    Log.d("AdChain", "Current user: ${currentUser?.userId}")
}

// SDK 초기화 상태 확인
if (AdchainSdk.isInitialized()) {
    // SDK가 초기화됨
}
```

### 로그아웃

```kotlin
AdchainSdk.logout(object : AdchainSdkLogoutListener {
    override fun onSuccess() {
        Log.d("AdChain", "Logout successful")
    }

    override fun onFailure(errorType: AdchainSdkLogoutListener.ErrorType) {
        Log.e("AdChain", "Logout failed: $errorType")
    }
})
```

### 에러 처리

SDK는 다음과 같은 에러 타입을 제공합니다:

```kotlin
enum class AdchainAdError {
    NOT_INITIALIZED,      // SDK가 초기화되지 않음
    NETWORK_ERROR,        // 네트워크 연결 실패
    INVALID_RESPONSE,     // 서버 응답 오류
    USER_NOT_LOGGED_IN,   // 사용자가 로그인하지 않음
    INVALID_PARAMETERS,   // 잘못된 파라미터
    UNKNOWN              // 알 수 없는 오류
}
```

## 🛡️ ProGuard 설정

ProGuard/R8을 사용하는 경우, `proguard-rules.pro` 파일에 다음 규칙을 추가하세요:

```proguard
# AdChain SDK
-keep class com.adchain.sdk.** { *; }
-keepclassmembers class com.adchain.sdk.** { *; }

# WebView JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
```

## 🐛 문제 해결

### 일반적인 문제와 해결 방법

#### 1. SDK 초기화 실패
- App Key와 App Secret이 올바른지 확인
- 네트워크 연결 상태 확인
- ProGuard 규칙이 올바르게 설정되었는지 확인

#### 2. Offerwall이 표시되지 않음
- 사용자가 로그인되었는지 확인 (`AdchainSdk.isLoggedIn`)
- 인터넷 권한이 추가되었는지 확인
- WebView 설정이 올바른지 확인

#### 3. 리워드를 받지 못함
- 서버와의 통신 로그 확인
- 미션/퀴즈 완료 콜백이 올바르게 호출되는지 확인

### 디버그 모드

디버그 모드를 활성화하여 상세한 로그를 확인할 수 있습니다:

```kotlin
val config = AdchainSdkConfig.Builder()
    .appKey("YOUR_APP_KEY")
    .appSecret("YOUR_APP_SECRET")
    .isDebugMode(true)  // 디버그 모드 활성화
    .build()
```

## 📞 지원

- **이슈 트래커**: [GitHub Issues](https://github.com/1selfworld-labs/adchain-sdk-android/issues)
- **이메일**: support@adchain.com
- **문서**: [개발자 가이드](https://docs.adchain.com)

## 📄 라이센스

```
Copyright 2025 AdChain

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```