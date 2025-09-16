# AdChain SDK Android 완벽한 배포 가이드

> **⚠️ 중요**: 이 가이드는 AI 또는 개발자가 실수 없이 Android SDK를 배포할 수 있도록 모든 단계를 상세히 설명합니다.
>
> **최종 업데이트**: 2025-09-16
> **작성 목적**: v1.0.10과 v1.0.11 배포 경험을 바탕으로 작성된 완벽한 가이드

---

## 📋 목차

1. [사전 요구사항](#사전-요구사항)
2. [배포 플랫폼 이해](#배포-플랫폼-이해)
3. [프로젝트 구조](#프로젝트-구조)
4. [배포 전 체크리스트](#배포-전-체크리스트)
5. [단계별 배포 프로세스](#단계별-배포-프로세스)
6. [검증 절차](#검증-절차)
7. [자동화 스크립트](#자동화-스크립트)
8. [트러블슈팅](#트러블슈팅)
9. [롤백 절차](#롤백-절차)

---

## 🔧 사전 요구사항

### 필수 도구 설치
```bash
# Java 17 (필수)
java -version  # OpenJDK 17 이상

# Android SDK
sdkmanager --list

# Git
git --version

# Gradle (프로젝트에 포함된 wrapper 사용 권장)
./gradlew --version
```

### 환경 설정
```bash
# JAVA_HOME 설정
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Android SDK 설정
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

### 인증 정보 설정
```bash
# local.properties 파일 생성 (Git에 추가하지 말 것!)
cat > local.properties << EOF
# Android SDK location
sdk.dir=/Users/[username]/Library/Android/sdk

# Maven Central (Sonatype) 인증
ossrhUsername=your_username
ossrhPassword=your_password

# GitHub Packages 인증
gpr.user=your_github_username
gpr.key=your_github_token

# GPG 서명 (Maven Central 필수)
signing.keyId=last8chars
signing.password=your_passphrase
signing.secretKeyRingFile=/path/to/secring.gpg
EOF
```

---

## 📦 배포 플랫폼 이해

### 1. JitPack (기본 배포)
- **URL**: https://jitpack.io/#1selfworld-labs/adchain-sdk-android
- **장점**: GitHub 연동 자동 빌드, 간편한 배포
- **단점**: 빌드 시간이 길 수 있음
- **사용법**:
  ```gradle
  // root build.gradle
  repositories {
      maven { url 'https://jitpack.io' }
  }

  // app build.gradle
  dependencies {
      implementation 'com.github.1selfworld-labs:adchain-sdk-android:v1.0.11'
  }
  ```

### 2. Maven Central (선택사항)
- **URL**: https://search.maven.org/
- **장점**: 표준 Maven 저장소, 빠른 다운로드
- **단점**: 복잡한 배포 과정, GPG 서명 필수
- **사용법**:
  ```gradle
  dependencies {
      implementation 'com.adchain.sdk:adchain-sdk:1.0.11'
  }
  ```

### 3. GitHub Packages (백업)
- **URL**: https://github.com/1selfworld-labs/adchain-sdk-android/packages
- **장점**: GitHub 통합, 프라이빗 배포 가능
- **단점**: 인증 필요
- **사용법**:
  ```gradle
  repositories {
      maven {
          url = uri("https://maven.pkg.github.com/1selfworld-labs/adchain-sdk-android")
          credentials {
              username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
              password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
          }
      }
  }
  ```

---

## 📂 프로젝트 구조

```
adchain-sdk-android/
├── gradle.properties           # ⚠️ SDK 버전 정의 (중요!)
├── jitpack.yml                # JitPack 빌드 설정
├── settings.gradle.kts        # 프로젝트 설정
├── build.gradle.kts           # 루트 빌드 스크립트
├── adchain-sdk/               # SDK 모듈
│   ├── build.gradle.kts      # 모듈 빌드 스크립트
│   ├── publish.gradle.kts    # 배포 설정
│   └── src/
│       └── main/
│           ├── java/com/adchain/sdk/  # 소스 코드
│           └── AndroidManifest.xml    # 매니페스트
└── sample/                    # 샘플 앱 (선택사항)
```

### 버전 관리 위치
1. **gradle.properties**: `SDK_VERSION=1.0.11` (메인 버전)
2. **build.gradle.kts**: `buildConfigField`로 버전 주입
3. **Git 태그**: `v1.0.11` (v 접두사 포함)

---

## ✅ 배포 전 체크리스트

### 코드 준비
```bash
□ 모든 기능 구현 완료
□ 컴파일 에러 없음
□ 테스트 통과
□ 코드 리뷰 완료
□ main 브랜치에 병합됨
```

### 버전 확인
```bash
□ gradle.properties의 SDK_VERSION 업데이트
□ CHANGELOG 또는 릴리스 노트 작성
□ 이전 버전과의 호환성 확인
```

### 빌드 테스트
```bash
□ ./gradlew clean build 성공
□ ./gradlew :adchain-sdk:assembleRelease 성공
□ 샘플 앱에서 로컬 SDK 테스트
```

---

## 📦 단계별 배포 프로세스

### STEP 1: 버전 업데이트
```bash
# 1.1 작업 디렉토리로 이동
cd /path/to/adchain-sdk-android

# 1.2 최신 코드 풀
git checkout main
git pull origin main

# 1.3 버전 업데이트
NEW_VERSION="1.0.12"  # 새 버전 번호

# gradle.properties 수정
sed -i '' "s/SDK_VERSION=.*/SDK_VERSION=$NEW_VERSION/" gradle.properties

# 또는 수동으로 편집
vim gradle.properties
# SDK_VERSION=1.0.12

# 1.4 변경사항 확인
git diff gradle.properties
```

### STEP 2: 빌드 및 테스트
```bash
# 2.1 클린 빌드
./gradlew clean

# 2.2 SDK 빌드
./gradlew :adchain-sdk:build

# 2.3 테스트 실행
./gradlew :adchain-sdk:test

# 2.4 AAR 파일 생성 확인
ls -la adchain-sdk/build/outputs/aar/
# adchain-sdk-release.aar 파일 확인

# 2.5 로컬 Maven 저장소에 설치 (테스트용)
./gradlew :adchain-sdk:publishToMavenLocal

# 2.6 설치 확인
ls -la ~/.m2/repository/com/adchain/sdk/adchain-sdk/$NEW_VERSION/
```

### STEP 3: 커밋 및 태그
```bash
# 3.1 변경사항 커밋
git add -A
git commit -m "chore: Bump version to v$NEW_VERSION

- Updated SDK_VERSION in gradle.properties
- [변경사항 설명]"

# 3.2 푸시
git push origin main

# 3.3 태그 생성 (⚠️ 'v' 접두사 필수!)
git tag -a "v$NEW_VERSION" -m "Release v$NEW_VERSION

[변경사항 상세 설명]
- Feature 1
- Feature 2
- Bug fixes"

# 3.4 태그 푸시
git push origin "v$NEW_VERSION"
```

### STEP 4: JitPack 배포 (자동)
```bash
# 4.1 JitPack 빌드 트리거
# 태그 푸시 후 자동으로 빌드 시작됨
# 또는 수동으로 트리거:
curl -X GET "https://jitpack.io/api/builds/com.github.1selfworld-labs/adchain-sdk-android/v$NEW_VERSION"

# 4.2 빌드 상태 확인 (웹에서)
echo "빌드 상태 확인: https://jitpack.io/#1selfworld-labs/adchain-sdk-android/v$NEW_VERSION"

# 4.3 빌드 로그 확인
echo "로그 확인: https://jitpack.io/com/github/1selfworld-labs/adchain-sdk-android/v$NEW_VERSION/build.log"
```

### STEP 5: Maven Central 배포 (선택사항)
```bash
# 5.1 서명된 아티팩트 생성
./gradlew :adchain-sdk:signReleasePublication

# 5.2 Maven Central에 업로드
./gradlew :adchain-sdk:publishReleasePublicationToSonatypeRepository

# 5.3 Staging 저장소 닫기 및 릴리스
# https://s01.oss.sonatype.org/ 에서 수동으로 진행
# 또는 gradle-nexus-publish-plugin 사용
```

### STEP 6: GitHub Packages 배포 (선택사항)
```bash
# 6.1 GitHub Packages에 배포
./gradlew :adchain-sdk:publishReleasePublicationToGitHubPackagesRepository

# 6.2 확인
echo "패키지 확인: https://github.com/1selfworld-labs/adchain-sdk-android/packages"
```

---

## 🔍 검증 절차

### 1. 버전 일치 검증
```bash
#!/bin/bash
# verify_version.sh

VERSION=$1
echo "=== 버전 $VERSION 검증 ==="

# gradle.properties 확인
GRADLE_VERSION=$(grep SDK_VERSION gradle.properties | cut -d'=' -f2)
echo "gradle.properties: $GRADLE_VERSION"

# Git 태그 확인
GIT_TAG=$(git describe --tags --abbrev=0)
echo "Git 태그: $GIT_TAG"

# 일치 여부
if [ "$GRADLE_VERSION" = "$VERSION" ] && [ "$GIT_TAG" = "v$VERSION" ]; then
    echo "✅ 버전 일치!"
else
    echo "❌ 버전 불일치!"
    exit 1
fi
```

### 2. JitPack 설치 테스트
```bash
# 2.1 테스트 프로젝트 생성
cd /tmp
mkdir test-adchain-android
cd test-adchain-android

# 2.2 build.gradle 생성
cat > build.gradle << EOF
plugins {
    id 'com.android.application' version '8.1.0' apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
EOF

# 2.3 settings.gradle 생성
cat > settings.gradle << EOF
rootProject.name = "TestAdchain"
include ':app'
EOF

# 2.4 app/build.gradle 생성
mkdir -p app
cat > app/build.gradle << EOF
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.test.adchain'
    compileSdk 34

    defaultConfig {
        applicationId "com.test.adchain"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
}

dependencies {
    implementation 'com.github.1selfworld-labs:adchain-sdk-android:v${VERSION}'
}
EOF

# 2.5 동기화 및 빌드
./gradlew build --refresh-dependencies
```

### 3. 기능 검증
```bash
# 3.1 AAR 파일 추출
cd /tmp
wget "https://jitpack.io/com/github/1selfworld-labs/adchain-sdk-android/v$VERSION/adchain-sdk-android-v$VERSION.aar"
unzip -l adchain-sdk-android-v$VERSION.aar

# 3.2 classes.jar 확인
unzip adchain-sdk-android-v$VERSION.aar classes.jar
jar tf classes.jar | grep "AdchainMission"

# 3.3 주요 클래스 존재 확인
jar tf classes.jar | grep -E "(AdchainSDK|AdchainMission|AdchainOfferwall)"
```

---

## 🤖 자동화 스크립트

### 완전 자동화 배포 스크립트
```bash
#!/bin/bash
# deploy_android_sdk.sh

set -e  # 에러 발생 시 중단

# 설정
NEW_VERSION=$1
if [ -z "$NEW_VERSION" ]; then
    echo "사용법: ./deploy_android_sdk.sh 1.0.12"
    exit 1
fi

echo "🚀 AdChain Android SDK v${NEW_VERSION} 배포 시작"

# 1. 최신 코드 풀
echo "📝 Step 1: 최신 코드 가져오기"
git checkout main
git pull origin main

# 2. 버전 업데이트
echo "📝 Step 2: 버전 업데이트"
sed -i '' "s/SDK_VERSION=.*/SDK_VERSION=$NEW_VERSION/" gradle.properties

# 3. 빌드 및 테스트
echo "🔨 Step 3: 빌드 및 테스트"
./gradlew clean
./gradlew :adchain-sdk:build
./gradlew :adchain-sdk:test

# 4. 커밋
echo "📤 Step 4: 커밋 및 푸시"
git add -A
git commit -m "chore: Bump version to v${NEW_VERSION}

- Updated SDK_VERSION to ${NEW_VERSION}
- Ready for release"

git push origin main

# 5. 태그 생성
echo "🏷️ Step 5: 태그 생성"
git tag -a "v${NEW_VERSION}" -m "Release v${NEW_VERSION}

Production release of AdChain SDK Android v${NEW_VERSION}"

git push origin "v${NEW_VERSION}"

# 6. JitPack 빌드 트리거
echo "📦 Step 6: JitPack 빌드 트리거"
curl -X GET "https://jitpack.io/api/builds/com.github.1selfworld-labs/adchain-sdk-android/v${NEW_VERSION}"

echo "✅ 배포 완료!"
echo "📋 다음 단계:"
echo "1. JitPack 빌드 확인: https://jitpack.io/#1selfworld-labs/adchain-sdk-android/v${NEW_VERSION}"
echo "2. 샘플 앱에서 테스트"
echo "3. Release notes 작성: https://github.com/1selfworld-labs/adchain-sdk-android/releases"
```

### 빌드 상태 확인 스크립트
```bash
#!/bin/bash
# check_jitpack_build.sh

VERSION=$1
if [ -z "$VERSION" ]; then
    echo "사용법: ./check_jitpack_build.sh v1.0.12"
    exit 1
fi

echo "🔍 JitPack 빌드 상태 확인: $VERSION"

# API 호출
RESPONSE=$(curl -s "https://jitpack.io/api/builds/com.github.1selfworld-labs/adchain-sdk-android/$VERSION")

# 상태 파싱
STATUS=$(echo $RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "UNKNOWN")

case $STATUS in
    "ok")
        echo "✅ 빌드 성공!"
        ;;
    "building")
        echo "🔨 빌드 중..."
        echo "로그: https://jitpack.io/com/github/1selfworld-labs/adchain-sdk-android/$VERSION/build.log"
        ;;
    "error")
        echo "❌ 빌드 실패!"
        echo "로그 확인: https://jitpack.io/com/github/1selfworld-labs/adchain-sdk-android/$VERSION/build.log"
        ;;
    *)
        echo "❓ 상태 불명: $STATUS"
        echo "웹에서 확인: https://jitpack.io/#1selfworld-labs/adchain-sdk-android/$VERSION"
        ;;
esac
```

### 로컬 테스트 스크립트
```bash
#!/bin/bash
# test_local_sdk.sh

echo "🧪 로컬 SDK 테스트"

# 1. 로컬 Maven에 설치
./gradlew :adchain-sdk:publishToMavenLocal

# 2. 샘플 앱에서 테스트
cd sample
# build.gradle에서 버전을 로컬 버전으로 변경
sed -i '' "s/implementation 'com.github.*/implementation 'com.adchain.sdk:adchain-sdk:+'/g" build.gradle

# 3. 빌드 및 실행
./gradlew clean assembleDebug

echo "✅ 로컬 테스트 완료"
echo "APK 위치: sample/build/outputs/apk/debug/sample-debug.apk"
```

---

## 🚨 트러블슈팅

### 문제 1: JitPack 빌드 실패
**증상**: JitPack에서 빌드 에러

**원인**:
- jitpack.yml 설정 오류
- JDK 버전 불일치
- 의존성 해결 실패

**해결**:
```bash
# 1. jitpack.yml 확인
cat jitpack.yml
# jdk: openjdk17 확인

# 2. 로컬에서 동일한 명령 실행
./gradlew clean
./gradlew :adchain-sdk:build
./gradlew :adchain-sdk:publishToMavenLocal

# 3. 빌드 로그 분석
curl https://jitpack.io/com/github/1selfworld-labs/adchain-sdk-android/[version]/build.log
```

### 문제 2: 버전 불일치
**증상**: 설치된 SDK 버전이 예상과 다름

**원인**:
- gradle.properties 업데이트 누락
- 캐시 문제

**해결**:
```bash
# 1. 버전 확인
grep SDK_VERSION gradle.properties

# 2. 캐시 클리어
./gradlew clean
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.github.1selfworld-labs/

# 3. 강제 새로고침
./gradlew build --refresh-dependencies
```

### 문제 3: 의존성 충돌
**증상**: Duplicate class 또는 충돌 에러

**원인**:
- 중복된 라이브러리
- 버전 충돌

**해결**:
```gradle
// app/build.gradle
android {
    packagingOptions {
        exclude 'META-INF/DEPENDENCIES'
        exclude 'META-INF/LICENSE'
        exclude 'META-INF/LICENSE.txt'
        exclude 'META-INF/NOTICE'
        exclude 'META-INF/NOTICE.txt'
    }
}

configurations.all {
    resolutionStrategy {
        force 'androidx.core:core-ktx:1.10.1'
        // 충돌하는 라이브러리 버전 강제 지정
    }
}
```

### 문제 4: ProGuard/R8 문제
**증상**: 릴리스 빌드에서 ClassNotFoundException

**원인**:
- ProGuard 규칙 누락

**해결**:
```proguard
# proguard-rules.pro
-keep class com.adchain.sdk.** { *; }
-keepclassmembers class com.adchain.sdk.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
```

### 문제 5: AndroidManifest 병합 실패
**증상**: Manifest merger failed

**원인**:
- 권한 충돌
- Application 클래스 충돌

**해결**:
```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 충돌 해결 -->
    <uses-permission android:name="android.permission.INTERNET"
        tools:node="merge" />

    <application
        tools:replace="android:theme"
        tools:merge="android:allowBackup">
    </application>
</manifest>
```

---

## ↩️ 롤백 절차

### 긴급 롤백
```bash
# 1. 이전 버전 태그로 체크아웃
git checkout v1.0.10

# 2. 핫픽스 브랜치 생성
git checkout -b hotfix/rollback-to-1.0.10

# 3. gradle.properties 버전 되돌리기
sed -i '' "s/SDK_VERSION=.*/SDK_VERSION=1.0.10/" gradle.properties

# 4. 커밋 및 푸시
git add -A
git commit -m "hotfix: Rollback to v1.0.10"
git push origin hotfix/rollback-to-1.0.10

# 5. 새 태그 생성 (패치 버전)
git tag -a "v1.0.10.1" -m "Hotfix: Rollback release"
git push origin "v1.0.10.1"
```

### JitPack 캐시 무효화
```bash
# JitPack 캐시 삭제 요청
curl -X DELETE "https://jitpack.io/api/builds/com.github.1selfworld-labs/adchain-sdk-android/v[문제버전]"
```

---

## 📊 배포 체크리스트 템플릿

```markdown
## 배포 체크리스트 v[VERSION]

### 사전 준비
- [ ] 코드 리뷰 완료
- [ ] 테스트 통과
- [ ] main 브랜치 최신화

### 버전 업데이트
- [ ] gradle.properties SDK_VERSION 업데이트
- [ ] 변경사항 문서화

### 빌드
- [ ] ./gradlew clean build 성공
- [ ] 로컬 테스트 완료

### 배포
- [ ] Git 커밋 및 푸시
- [ ] Git 태그 생성 (v 접두사)
- [ ] JitPack 빌드 성공 확인

### 검증
- [ ] 샘플 앱 테스트
- [ ] 의존성 설치 테스트
- [ ] 주요 기능 동작 확인

### 문서화
- [ ] Release notes 작성
- [ ] CHANGELOG 업데이트
- [ ] 샘플 코드 업데이트
```

---

## 📌 핵심 주의사항 요약

### ⚠️ 절대 잊지 말아야 할 것들

1. **버전 관리**
   - `gradle.properties`의 `SDK_VERSION` 항상 업데이트
   - Git 태그는 반드시 `v` 접두사 포함 (예: v1.0.12)

2. **JitPack 배포**
   - 태그 푸시 후 자동 빌드 시작
   - 빌드 완료까지 5-10분 소요
   - 빌드 로그 확인 필수

3. **테스트**
   - 로컬 Maven 설치로 먼저 테스트
   - 샘플 앱에서 실제 동작 확인
   - --refresh-dependencies 플래그 사용

4. **트러블슈팅**
   - JDK 17 사용 확인
   - 캐시 문제 시 ~/.gradle/caches 삭제
   - jitpack.yml 설정 확인

---

## 🔗 유용한 링크

- **JitPack 대시보드**: https://jitpack.io/#1selfworld-labs/adchain-sdk-android
- **GitHub 저장소**: https://github.com/1selfworld-labs/adchain-sdk-android
- **빌드 상태**: https://jitpack.io/v/1selfworld-labs/adchain-sdk-android
- **Maven Central**: https://search.maven.org (사용 시)

---

## 📞 문의

문제 발생 시:
- GitHub Issues: https://github.com/1selfworld-labs/adchain-sdk-android/issues
- 이메일: fly33499@gmail.com

---

## 📝 부록: 샘플 통합 코드

### Gradle 설정
```gradle
// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// app/build.gradle
dependencies {
    implementation 'com.github.1selfworld-labs:adchain-sdk-android:v1.0.11'
}
```

### 초기화 코드
```kotlin
// Application 클래스
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // SDK 초기화
        AdchainSDK.init(
            context = this,
            publisherId = "YOUR_PUBLISHER_ID",
            config = AdchainConfig.Builder()
                .setDebugMode(BuildConfig.DEBUG)
                .build()
        )
    }
}

// Activity에서 사용
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Offerwall 표시
        AdchainOfferwall.show(this)

        // Mission 시작
        AdchainMission.startMission(missionId = "mission_001")
    }
}
```

---

**마지막 팁**: Android SDK 배포는 JitPack을 통해 자동화되어 있어 iOS보다 간단합니다. 하지만 버전 관리와 빌드 검증은 여전히 중요합니다. 각 단계를 확실히 확인하고 진행하세요! 🚀