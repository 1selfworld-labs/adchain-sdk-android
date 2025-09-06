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

// Publishing configuration
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.adchain"
            artifactId = "adchain-sdk"
            version = project.findProperty("SDK_VERSION") as String? ?: "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Adchain SDK")
                description.set("Android SDK for Adchain advertising platform")
                url.set("https://github.com/yourusername/adchain-sdk-android")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("adchain")
                        name.set("Adchain Team")
                        email.set("dev@adchain.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:github.com/yourusername/adchain-sdk-android.git")
                    developerConnection.set("scm:git:ssh://github.com/yourusername/adchain-sdk-android.git")
                    url.set("https://github.com/yourusername/adchain-sdk-android/tree/main")
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
                username = localProperties.getProperty("ossrhUsername") ?: System.getenv("OSSRH_USERNAME")
                password = localProperties.getProperty("ossrhPassword") ?: System.getenv("OSSRH_PASSWORD")
            }
        }
        
        // Alternative: GitHub Packages
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yourusername/adchain-sdk-android")
            credentials {
                username = localProperties.getProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
                password = localProperties.getProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Signing configuration for Maven Central
signing {
    val signingKeyId = localProperties.getProperty("signing.keyId")
    val signingPassword = localProperties.getProperty("signing.password")
    val signingSecretKeyRingFile = localProperties.getProperty("signing.secretKeyRingFile")
    
    if (signingKeyId != null && signingPassword != null && signingSecretKeyRingFile != null) {
        useInMemoryPgpKeys(signingKeyId, File(signingSecretKeyRingFile).readText(), signingPassword)
        sign(publishing.publications["release"])
    }
}