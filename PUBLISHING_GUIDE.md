# Adchain SDK 배포 가이드

## 📦 배포 준비 체크리스트

### 1. 필수 계정 준비
- [ ] Sonatype JIRA 계정 생성 (Maven Central)
- [ ] GitHub 계정 (GitHub Packages)
- [ ] GPG 키 생성 (Maven Central 서명용)

## 🚀 배포 방법

### 옵션 1: Maven Central 배포 (권장)

#### 1. Sonatype 계정 설정

1. [Sonatype JIRA](https://issues.sonatype.org)에서 계정 생성
2. 새 이슈 생성하여 `com.adchain` 그룹 ID 요청
3. 승인 대기 (24-48시간)

#### 2. GPG 키 생성

```bash
# GPG 키 생성
gpg --gen-key

# 키 ID 확인
gpg --list-secret-keys --keyid-format SHORT

# 공개 키 서버에 업로드
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

#### 3. local.properties 설정

```properties
# Maven Central 자격 증명
ossrhUsername=your-sonatype-username
ossrhPassword=your-sonatype-password

# GPG 서명 정보
signing.keyId=YOUR_KEY_ID
signing.password=your-gpg-password
signing.secretKeyRingFile=/Users/yourname/.gnupg/secring.gpg
```

#### 4. 배포 실행

```bash
# 빌드 및 배포
./gradlew :adchain-sdk:publishReleasePublicationToSonatypeRepository

# Staging 저장소 닫기 및 릴리스
./gradlew closeAndReleaseRepository
```

### 옵션 2: GitHub Packages 배포

#### 1. GitHub Personal Access Token 생성

1. GitHub Settings → Developer settings → Personal access tokens
2. `write:packages` 권한으로 새 토큰 생성

#### 2. local.properties 설정

```properties
gpr.user=your-github-username
gpr.key=your-github-token
```

#### 3. 배포 실행

```bash
./gradlew :adchain-sdk:publishReleasePublicationToGitHubPackagesRepository
```

### 옵션 3: 프라이빗 Maven 저장소 (자체 호스팅)

#### 1. Nexus 또는 Artifactory 서버 구축

#### 2. publish.gradle.kts 수정

```kotlin
repositories {
    maven {
        name = "private"
        url = uri("https://your-maven-repo.com/repository/maven-releases/")
        credentials {
            username = "your-username"
            password = "your-password"
        }
    }
}
```

#### 3. 배포 실행

```bash
./gradlew :adchain-sdk:publishReleasePublicationToPrivateRepository
```

## 📱 로컬 테스트

배포 전 로컬에서 테스트:

```bash
# AAR 파일 생성
./gradlew :adchain-sdk:assembleRelease

# 로컬 Maven 저장소에 설치
./gradlew :adchain-sdk:publishToMavenLocal
```

테스트 프로젝트의 `build.gradle.kts`:
```kotlin
repositories {
    mavenLocal() // 로컬 저장소 추가
    mavenCentral()
}

dependencies {
    implementation("com.adchain:adchain-sdk:1.0.0")
}
```

## 📈 버전 관리

`gradle.properties`에서 버전 설정:
```properties
SDK_VERSION=1.0.0
```

버전 규칙:
- Major: 큰 변경사항, 하위 호환성 없음
- Minor: 새 기능 추가, 하위 호환성 유지
- Patch: 버그 수정

## 🔄 CI/CD 자동화

### GitHub Actions 예제

`.github/workflows/publish.yml`:
```yaml
name: Publish SDK

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
        
      - name: Publish to Maven Central
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          SIGNING_KEY_ID: ${{ secrets.SIGNING_KEY_ID }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
        run: |
          ./gradlew publishReleasePublicationToSonatypeRepository
```

## 📊 배포 후 확인

### Maven Central
- 검색: https://search.maven.org/search?q=g:com.adchain
- 동기화 시간: 2-24시간

### GitHub Packages
- 패키지 페이지: https://github.com/yourusername/adchain-sdk-android/packages

## 🐛 트러블슈팅

### 일반적인 문제들

1. **서명 실패**
   ```bash
   # GPG 키 재생성
   gpg --full-generate-key
   ```

2. **401 Unauthorized**
   - 자격 증명 확인
   - 토큰 권한 확인

3. **버전 충돌**
   - 이미 배포된 버전은 덮어쓸 수 없음
   - 버전 번호 증가 필요

## 📝 배포 체크리스트

배포 전 확인:
- [ ] 버전 번호 업데이트
- [ ] CHANGELOG 작성
- [ ] 모든 테스트 통과
- [ ] 문서 업데이트
- [ ] ProGuard 규칙 확인
- [ ] API 키 및 시크릿 제거
- [ ] 샘플 앱 테스트

## 🔗 유용한 링크

- [Sonatype OSS Repository Hosting](https://central.sonatype.org/publish/publish-guide/)
- [GitHub Packages Documentation](https://docs.github.com/en/packages)
- [Gradle Publishing Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)