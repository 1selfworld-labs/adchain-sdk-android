# Adchain SDK Android 통합 가이드

## 📋 요구사항

- Android 7.0 (API 24) 이상
- compileSdkVersion 35
- Kotlin 1.9.25+
- Gradle 8.10.2+

## 🚀 SDK 설치

### 방법 1: Maven Central (권장)

`settings.gradle.kts`에 Maven Central 저장소 추가:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

모듈의 `build.gradle.kts`에 의존성 추가:
```kotlin
dependencies {
    implementation("com.adchain:adchain-sdk:1.0.0")
}
```

### 방법 2: GitHub Packages

`settings.gradle.kts`에 GitHub Packages 저장소 추가:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/yourusername/adchain-sdk-android")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
            }
        }
    }
}
```

### 방법 3: 로컬 AAR 파일

1. 릴리스 AAR 파일 다운로드
2. 프로젝트의 `libs` 폴더에 복사
3. 의존성 추가:
```kotlin
dependencies {
    implementation(files("libs/adchain-sdk-1.0.0.aar"))
    
    // 필수 의존성들
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
```

## 🔧 SDK 초기화

### 1. Application 클래스 생성

```kotlin
import android.app.Application
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.core.AdchainSdkConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // SDK 초기화
        val config = AdchainSdkConfig.Builder("YOUR_APP_KEY")
            .appSecret("YOUR_APP_SECRET")
            .debugMode(BuildConfig.DEBUG) // 디버그 모드 설정
            .build()
            
        AdchainSdk.initialize(this, config)
    }
}
```

### 2. AndroidManifest.xml 설정

```xml
<application
    android:name=".MyApplication"
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/Theme.AppCompat">
    
    <!-- 인터넷 권한 필수 -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <!-- 광고 ID 접근 권한 (선택) -->
    <uses-permission android:name="com.google.android.gms.permission.AD_ID" />
    
    <!-- SDK 내부 Activity 자동 등록됨 -->
</application>
```

## 👤 사용자 로그인

```kotlin
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.core.AdchainSdkUser
import com.adchain.sdk.core.AdchainSdkLoginListener

// 사용자 정보 생성
val user = AdchainSdkUser(
    userId = "unique_user_id",
    gender = AdchainSdkUser.Gender.MALE,  // MALE, FEMALE, UNKNOWN
    birthYear = 1990
)

// 로그인
AdchainSdk.login(user, object : AdchainSdkLoginListener {
    override fun onSuccess() {
        Log.d("AdchainSDK", "로그인 성공")
    }
    
    override fun onFailure(errorType: AdchainSdkLoginListener.ErrorType) {
        when (errorType) {
            AdchainSdkLoginListener.ErrorType.NOT_INITIALIZED -> {
                Log.e("AdchainSDK", "SDK가 초기화되지 않음")
            }
            AdchainSdkLoginListener.ErrorType.INVALID_USER_ID -> {
                Log.e("AdchainSDK", "잘못된 사용자 ID")
            }
            AdchainSdkLoginListener.ErrorType.AUTHENTICATION_FAILED -> {
                Log.e("AdchainSDK", "인증 실패")
            }
        }
    }
})
```

## 📱 주요 기능

### 1. 오퍼월 (Offerwall)

```kotlin
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.offerwall.OfferwallCallback

// 오퍼월 열기
AdchainSdk.openOfferwall(context, object : OfferwallCallback {
    override fun onOpened() {
        Log.d("Offerwall", "오퍼월 열림")
    }
    
    override fun onClosed() {
        Log.d("Offerwall", "오퍼월 닫힘")
    }
    
    override fun onError(message: String) {
        Log.e("Offerwall", "오류: $message")
    }
    
    override fun onRewardEarned(amount: Int, currency: String) {
        Log.d("Offerwall", "리워드 획득: $amount $currency")
    }
})
```

### 2. 퀴즈 광고

```kotlin
import com.adchain.sdk.quiz.AdchainQuiz
import com.adchain.sdk.quiz.AdchainQuizEventsListener

val quiz = AdchainQuiz()

// 퀴즈 이벤트 리스너 설정
quiz.setEventsListener(object : AdchainQuizEventsListener {
    override fun onAdReady() {
        Log.d("Quiz", "퀴즈 준비됨")
        quiz.show()
    }
    
    override fun onAdShown() {
        Log.d("Quiz", "퀴즈 표시됨")
    }
    
    override fun onAdClosed() {
        Log.d("Quiz", "퀴즈 닫힘")
    }
    
    override fun onAdRewarded(reward: AdchainQuizEventsListener.Reward) {
        Log.d("Quiz", "리워드: ${reward.amount} ${reward.type}")
    }
    
    override fun onAdError(error: AdchainAdError) {
        Log.e("Quiz", "오류: ${error.message}")
    }
})

// 퀴즈 로드
quiz.load()
```

### 3. 미션 광고

```kotlin
import com.adchain.sdk.mission.AdchainMission
import com.adchain.sdk.mission.AdchainMissionEventsListener

val mission = AdchainMission()

// 미션 이벤트 리스너 설정
mission.setEventsListener(object : AdchainMissionEventsListener {
    override fun onAdReady() {
        Log.d("Mission", "미션 준비됨")
        mission.show()
    }
    
    override fun onAdShown() {
        Log.d("Mission", "미션 표시됨")
    }
    
    override fun onAdClosed() {
        Log.d("Mission", "미션 닫힘")
    }
    
    override fun onMissionCompleted(missionId: String, reward: Int) {
        Log.d("Mission", "미션 완료: $missionId, 리워드: $reward")
    }
    
    override fun onAdError(error: AdchainAdError) {
        Log.e("Mission", "오류: ${error.message}")
    }
})

// 미션 로드
mission.load()
```

## 🔍 ProGuard 설정

ProGuard를 사용하는 경우, 다음 규칙을 추가하세요:

```proguard
# Adchain SDK
-keep class com.adchain.sdk.** { *; }
-keepclassmembers class com.adchain.sdk.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
```

## 📊 이벤트 추적

SDK는 자동으로 다음 이벤트들을 추적합니다:
- SDK 초기화
- 사용자 로그인/로그아웃
- 세션 시작
- 오퍼월 열기/닫기
- 광고 노출 및 클릭
- 리워드 획득

## 🐛 디버깅

디버그 모드를 활성화하면 상세한 로그를 확인할 수 있습니다:

```kotlin
val config = AdchainSdkConfig.Builder("YOUR_APP_KEY")
    .appSecret("YOUR_APP_SECRET")
    .debugMode(true)
    .build()
```

## 📝 체크리스트

SDK 통합 전 확인사항:
- [ ] App Key와 App Secret 발급 받기
- [ ] 최소 API 레벨 24 이상 설정
- [ ] 인터넷 권한 추가
- [ ] Application 클래스에서 SDK 초기화
- [ ] 사용자 로그인 구현
- [ ] ProGuard 규칙 추가 (Release 빌드 시)

## 🆘 문의사항

- 이메일: support@adchain.com
- 문서: https://docs.adchain.com
- GitHub Issues: https://github.com/yourusername/adchain-sdk-android/issues

## 📄 라이선스

Apache License 2.0