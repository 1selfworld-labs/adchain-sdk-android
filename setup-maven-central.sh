#!/bin/bash

echo "🔐 Maven Central 배포를 위한 GPG 키 설정"
echo "========================================="

# 1. GPG 키 생성
echo "📝 GPG 키를 생성합니다..."
echo "다음 정보를 입력해주세요:"
echo "- Real name: AdChain Team"
echo "- Email: dev@adchain.com"
echo "- Passphrase: (안전한 비밀번호 입력)"

cat >gpg-batch <<EOF
%echo Generating GPG key for Maven Central
Key-Type: RSA
Key-Length: 4096
Subkey-Type: RSA
Subkey-Length: 4096
Name-Real: AdChain Team
Name-Email: dev@adchain.com
Expire-Date: 2y
%commit
%echo done
EOF

gpg --batch --generate-key gpg-batch
rm gpg-batch

# 2. 키 ID 확인
echo ""
echo "🔑 생성된 GPG 키 목록:"
gpg --list-secret-keys --keyid-format=long

echo ""
echo "📋 키 ID를 복사해주세요 (sec 다음 줄의 16자리 문자):"
echo "예시: ABCDEF1234567890"
read -p "키 ID 입력: " KEY_ID

# 3. 공개키 서버에 업로드
echo ""
echo "📤 공개키를 키 서버에 업로드합니다..."
gpg --keyserver keyserver.ubuntu.com --send-keys $KEY_ID

# 4. 비밀키 내보내기
echo ""
echo "💾 비밀키를 파일로 내보냅니다..."
gpg --armor --export-secret-keys $KEY_ID > maven-central-secret.asc

# 5. local.properties 생성
echo ""
echo "📝 local.properties 파일을 생성합니다..."
read -p "Sonatype 사용자명 입력: " OSSRH_USERNAME
read -s -p "Sonatype 비밀번호 입력: " OSSRH_PASSWORD
echo ""
read -s -p "GPG 키 비밀번호 입력: " GPG_PASSWORD
echo ""

cat >local.properties <<EOF
# Maven Central (Sonatype) credentials
ossrhUsername=$OSSRH_USERNAME
ossrhPassword=$OSSRH_PASSWORD

# GPG signing
signing.keyId=$KEY_ID
signing.password=$GPG_PASSWORD
signing.secretKeyRingFile=$PWD/maven-central-secret.asc
EOF

echo ""
echo "✅ 설정 완료!"
echo ""
echo "⚠️  중요: 다음 파일들을 안전하게 보관하고 Git에 커밋하지 마세요:"
echo "  - local.properties"
echo "  - maven-central-secret.asc"
echo ""
echo "🚀 이제 다음 명령으로 배포할 수 있습니다:"
echo "  ./gradlew publishReleasePublicationToSonatypeRepository"