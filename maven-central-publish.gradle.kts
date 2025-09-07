// Maven Central 배포 설정
// 소스코드는 private, 바이너리는 public으로 배포

import java.util.Properties

plugins {
    id("maven-publish")
    id("signing")
}

// Load local properties for signing
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "io.github.1selfworld-labs"  // Maven Central은 도메인 검증 필요
                artifactId = "adchain-sdk"
                version = project.findProperty("SDK_VERSION") as String? ?: "1.0.0"

                from(components["release"])

                pom {
                    name.set("AdChain SDK")
                    description.set("Android SDK for AdChain advertising platform")
                    url.set("https://github.com/1selfworld-labs/adchain-sdk-android")
                    
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("adchain")
                            name.set("AdChain Team")
                            email.set("dev@adchain.com")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:github.com/1selfworld-labs/adchain-sdk-android.git")
                        developerConnection.set("scm:git:ssh://github.com/1selfworld-labs/adchain-sdk-android.git")
                        url.set("https://github.com/1selfworld-labs/adchain-sdk-android/tree/main")
                    }
                }
            }
        }
        
        repositories {
            maven {
                name = "sonatype"
                val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
                
                credentials {
                    username = localProperties.getProperty("ossrhUsername") 
                        ?: System.getenv("OSSRH_USERNAME")
                    password = localProperties.getProperty("ossrhPassword") 
                        ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }
}

// Signing configuration for Maven Central (required)
signing {
    val signingKeyId = localProperties.getProperty("signing.keyId")
        ?: System.getenv("SIGNING_KEY_ID")
    val signingPassword = localProperties.getProperty("signing.password")
        ?: System.getenv("SIGNING_PASSWORD")
    val signingKey = localProperties.getProperty("signing.key")
        ?: System.getenv("SIGNING_KEY")
    
    if (signingKeyId != null && signingPassword != null && signingKey != null) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(publishing.publications["release"])
    }
}

// 배포 명령어:
// ./gradlew publishReleasePublicationToSonatypeRepository
// 그 다음 https://s01.oss.sonatype.org 에서 Release