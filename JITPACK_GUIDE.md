# 🚀 AdChain SDK - JitPack 배포 및 사용 가이드

## 📋 SDK 배포자용 (1selfworld-labs 팀)

### 1. 새 버전 릴리스 방법

#### Option A: GitHub Release 사용 (권장)
1. GitHub 저장소 페이지에서 "Releases" 클릭
2. "Create a new release" 클릭
3. Tag version 입력 (예: `1.0.0`, `1.0.1`)
4. Release title과 설명 작성
5. "Publish release" 클릭
6. JitPack이 자동으로 빌드 시작 (약 2-5분 소요)

#### Option B: Git Tag 사용
```bash
# 새 버전 태그 생성
git tag -a 1.0.0 -m "Release version 1.0.0"

# 태그 푸시
git push origin 1.0.0
```

### 2. 빌드 상태 확인

1. https://jitpack.io/#1selfworld-labs/adchain-sdk-android 접속
2. 빌드 상태 확인 (초록색 = 성공, 빨간색 = 실패)
3. "Get it" 버튼으로 사용 가능 확인

### 3. Private Repository 설정

JitPack Private 사용시:
1. https://jitpack.io/private 에서 인증 토큰 생성
2. 파트너사에 토큰 제공

---

## 👥 SDK 사용자용 (파트너사)

### 1. 프로젝트 설정

#### **build.gradle.kts (Project level)**
```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

#### **build.gradle.kts (App level)**
```kotlin
dependencies {
    // AdChain SDK - 모든 의존성 자동 포함!
    implementation("com.github.1selfworld-labs:adchain-sdk-android:1.0.0")
}
```

### 2. Private Repository 접근 (필요시)

#### **gradle.properties**
```properties
authToken=jp_xxxxxxxxxxxxxxxxxx
```

#### **build.gradle.kts**
```kotlin
repositories {
    maven {
        url = uri("https://jitpack.io")
        credentials { username = authToken }
    }
}
```

### 3. SDK 초기화

```kotlin
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.core.AdchainSdkConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = AdchainSdkConfig(
            appKey = "YOUR_APP_KEY",
            appSecret = "YOUR_APP_SECRET"
        )
        
        AdchainSdk.initialize(this, config)
    }
}
```

### 4. AndroidManifest.xml 권한 추가

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

---

## ✅ 장점

### JitPack vs 직접 AAR 배포

| 구분 | JitPack | AAR 파일 |
|------|---------|----------|
| **의존성 관리** | ✅ 자동 | ❌ 수동 |
| **버전 관리** | ✅ Git 태그 | ❌ 파일명 |
| **업데이트** | ✅ 버전만 변경 | ❌ 파일 교체 |
| **설정 복잡도** | ⭐ 간단 | ⭐⭐⭐ 복잡 |
| **Private 지원** | ✅ 토큰 인증 | - |

---

## 🔧 문제 해결

### 빌드 실패시
1. https://jitpack.io/#1selfworld-labs/adchain-sdk-android 에서 로그 확인
2. jitpack.yml 파일 확인
3. gradlew 실행 권한 확인

### 의존성 충돌시
```kotlin
implementation("com.github.1selfworld-labs:adchain-sdk-android:1.0.0") {
    exclude(group = "com.squareup.okhttp3")
}
```

### 캐시 문제시
```bash
./gradlew build --refresh-dependencies
```

---

## 📞 지원

- **이슈**: https://github.com/1selfworld-labs/adchain-sdk-android/issues
- **JitPack 상태**: https://jitpack.io/#1selfworld-labs/adchain-sdk-android
- **이메일**: dev@adchain.com

---

## 🎯 빠른 시작 체크리스트

### SDK 배포자 (1selfworld-labs)
- [ ] 코드 수정 완료
- [ ] 버전 번호 업데이트 (gradle.properties)
- [ ] Git 커밋 & 푸시
- [ ] GitHub Release 생성 또는 Tag 푸시
- [ ] JitPack 빌드 확인
- [ ] 파트너사에 새 버전 공지

### SDK 사용자 (파트너사)
- [ ] JitPack repository 추가
- [ ] dependency 추가 (한 줄!)
- [ ] SDK 초기화 코드 작성
- [ ] 권한 설정
- [ ] 테스트 실행