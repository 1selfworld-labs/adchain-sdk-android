// GitHub Release를 통한 Public 배포
// Private repo의 Release 자산은 public 접근 가능

import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

tasks.register("createPublicRelease") {
    group = "publishing"
    description = "Create AAR for public distribution via GitHub Releases"
    
    dependsOn("assembleRelease")
    
    doLast {
        val version = project.findProperty("SDK_VERSION") as String? ?: "1.0.0"
        val releaseDir = file("$buildDir/releases")
        releaseDir.mkdirs()
        
        // AAR 복사
        copy {
            from("$buildDir/outputs/aar/adchain-sdk-release.aar")
            into(releaseDir)
            rename { "adchain-sdk-$version.aar" }
        }
        
        // POM 파일 생성 (의존성 정보 포함)
        val pomFile = file("$releaseDir/adchain-sdk-$version.pom")
        pomFile.writeText("""
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" 
         xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.adchain.sdk</groupId>
  <artifactId>adchain-sdk</artifactId>
  <version>$version</version>
  <packaging>aar</packaging>
  
  <dependencies>
    <dependency>
      <groupId>com.squareup.retrofit2</groupId>
      <artifactId>retrofit</artifactId>
      <version>2.9.0</version>
    </dependency>
    <dependency>
      <groupId>com.squareup.retrofit2</groupId>
      <artifactId>converter-moshi</artifactId>
      <version>2.9.0</version>
    </dependency>
    <dependency>
      <groupId>com.squareup.okhttp3</groupId>
      <artifactId>okhttp</artifactId>
      <version>4.12.0</version>
    </dependency>
    <dependency>
      <groupId>com.squareup.okhttp3</groupId>
      <artifactId>logging-interceptor</artifactId>
      <version>4.12.0</version>
    </dependency>
    <dependency>
      <groupId>com.squareup.moshi</groupId>
      <artifactId>moshi</artifactId>
      <version>1.15.0</version>
    </dependency>
    <dependency>
      <groupId>com.squareup.moshi</groupId>
      <artifactId>moshi-kotlin</artifactId>
      <version>1.15.0</version>
    </dependency>
    <dependency>
      <groupId>com.github.bumptech.glide</groupId>
      <artifactId>glide</artifactId>
      <version>4.16.0</version>
    </dependency>
    <dependency>
      <groupId>com.google.android.gms</groupId>
      <artifactId>play-services-ads-identifier</artifactId>
      <version>18.0.1</version>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlinx</groupId>
      <artifactId>kotlinx-coroutines-android</artifactId>
      <version>1.7.3</version>
    </dependency>
  </dependencies>
</project>
        """.trimIndent())
        
        println("✅ Public release files created in: $releaseDir")
        println("📦 Files to upload to GitHub Release:")
        println("   - adchain-sdk-$version.aar")
        println("   - adchain-sdk-$version.pom")
    }
}