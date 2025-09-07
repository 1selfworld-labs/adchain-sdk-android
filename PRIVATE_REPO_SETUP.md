# 🔐 AdChain SDK Private Repository 설정 가이드

## 📋 파트너사 설정 방법

AdChain SDK는 private repository로 제공됩니다. 아래 단계를 따라 설정해주세요.

### Step 1: JitPack 인증 토큰 설정

#### 방법 1: gradle.properties 사용 (권장)

**~/.gradle/gradle.properties** 파일에 다음 내용 추가:
```properties
# AdChain SDK JitPack 인증 토큰
authToken=제공받은_토큰_값
```

> 💡 **주의**: 제공받은 토큰을 정확히 입력해주세요.
> 토큰 예시: `jp_xxxxxxxxxxxxxxxxxx`

#### 방법 2: 프로젝트별 설정

프로젝트 루트의 **gradle.properties** 파일에 추가:
```properties
authToken=제공받은_토큰_값
```

> ⚠️ **보안 주의**: 이 파일을 Git에 커밋하지 마세요!

### Step 2: 프로젝트 설정

#### **settings.gradle.kts** 수정
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            credentials { 
                username = providers.gradleProperty("authToken").orNull
                    ?: System.getenv("JITPACK_AUTH_TOKEN")
            }
        }
    }
}
```

#### **app/build.gradle.kts** 수정
```kotlin
dependencies {
    // AdChain SDK
    implementation("com.github.1selfworld-labs:adchain-sdk-android:1.0.0")
}
```

### Step 3: SDK 초기화

```kotlin
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.core.AdchainSdkConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = AdchainSdkConfig(
            appKey = "YOUR_APP_KEY",      // 제공받은 앱 키
            appSecret = "YOUR_APP_SECRET"  // 제공받은 앱 시크릿
        )
        
        AdchainSdk.initialize(this, config)
    }
}
```

### Step 4: AndroidManifest.xml 권한 추가

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

---

## 🔧 CI/CD 환경 설정

### GitHub Actions
```yaml
env:
  JITPACK_AUTH_TOKEN: ${{ secrets.JITPACK_AUTH_TOKEN }}
```

### Jenkins
```groovy
environment {
    JITPACK_AUTH_TOKEN = credentials('jitpack-auth-token')
}
```

---

## ❓ 자주 묻는 질문

### Q: 401 Unauthorized 오류가 발생해요
**A:** 토큰이 올바르게 설정되었는지 확인하세요:
```bash
# 토큰 확인
cat ~/.gradle/gradle.properties | grep authToken
```

### Q: 패키지를 찾을 수 없다고 나와요
**A:** 다음을 확인하세요:
1. Private repository 접근 권한
2. 올바른 버전 번호
3. JitPack 빌드 성공 여부

### Q: 로컬에서는 되는데 CI에서 실패해요
**A:** CI 환경변수에 `JITPACK_AUTH_TOKEN` 설정 필요

---

## 📞 지원

문제가 계속되면 다음 정보와 함께 연락주세요:
- 에러 메시지 전체
- build.gradle 파일
- 사용 중인 SDK 버전

**연락처:**
- 이메일: dev@adchain.com
- Slack: #adchain-sdk-support

---

## 🔒 보안 체크리스트

- [ ] 토큰을 코드에 하드코딩하지 않았나요?
- [ ] `.gitignore`에 `gradle.properties` 추가했나요?
- [ ] CI/CD 시크릿 설정했나요?
- [ ] 토큰 권한이 읽기 전용인가요?