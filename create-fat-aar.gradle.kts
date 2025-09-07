import com.android.build.gradle.internal.tasks.BundleAar
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Fat AAR creation script
// This bundles all dependencies into a single AAR file

tasks.register<Jar>("createFatAar") {
    description = "Creates a Fat AAR with all dependencies included"
    group = "build"
    
    dependsOn("assembleRelease")
    
    val releaseAar = file("adchain-sdk/build/outputs/aar/adchain-sdk-release.aar")
    val outputAar = file("adchain-sdk/build/outputs/aar/adchain-sdk-standalone.aar")
    
    doLast {
        if (!releaseAar.exists()) {
            throw GradleException("Release AAR not found. Run 'assembleRelease' first.")
        }
        
        val tempDir = file("$buildDir/tmp/fatAar")
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        
        // Extract original AAR
        copy {
            from(zipTree(releaseAar))
            into(tempDir)
        }
        
        // Create libs directory
        val libsDir = file("$tempDir/libs")
        libsDir.mkdirs()
        
        // Copy all dependency JARs
        configurations.getByName("releaseRuntimeClasspath").forEach { file ->
            if (file.name.endsWith(".jar")) {
                copy {
                    from(file)
                    into(libsDir)
                }
            }
        }
        
        // Repackage as new AAR
        ZipOutputStream(outputAar.outputStream()).use { zip ->
            tempDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(tempDir).path.replace("\\", "/")
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        
        tempDir.deleteRecursively()
        
        println("✅ Standalone AAR created: ${outputAar.absolutePath}")
        println("📦 File size: ${outputAar.length() / 1024} KB")
    }
}

// Alternative: Create a distribution package with AAR + dependencies list
tasks.register<Zip>("createDistributionPackage") {
    description = "Creates a distribution package with AAR and setup files"
    group = "distribution"
    
    dependsOn("assembleRelease")
    
    archiveBaseName.set("adchain-sdk-distribution")
    archiveVersion.set(project.findProperty("SDK_VERSION") as String? ?: "1.0.0")
    destinationDirectory.set(file("$buildDir/distributions"))
    
    from("adchain-sdk/build/outputs/aar") {
        include("adchain-sdk-release.aar")
        into("lib")
    }
    
    // Create dependencies.gradle with all required dependencies
    from(createDependenciesFile()) {
        into(".")
    }
    
    // Include integration guide
    from("INTEGRATION_GUIDE.md") {
        into("docs")
    }
    
    // Create sample integration file
    from(createSampleBuildFile()) {
        into("sample")
    }
}

fun createDependenciesFile(): File {
    val depsFile = file("$buildDir/tmp/dependencies.gradle")
    depsFile.parentFile.mkdirs()
    
    depsFile.writeText("""
// Add these dependencies to your app's build.gradle.kts
dependencies {
    // AdChain SDK
    implementation(files("libs/adchain-sdk-release.aar"))
    
    // Required dependencies
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
}
    """.trimIndent())
    
    return depsFile
}

fun createSampleBuildFile(): File {
    val sampleFile = file("$buildDir/tmp/sample-integration.kt")
    sampleFile.parentFile.mkdirs()
    
    sampleFile.writeText("""
// Sample integration code
import com.adchain.sdk.core.AdchainSdk
import com.adchain.sdk.core.AdchainSdkConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdChain SDK
        val config = AdchainSdkConfig(
            appKey = "YOUR_APP_KEY",
            appSecret = "YOUR_APP_SECRET"
        )
        
        AdchainSdk.initialize(this, config)
    }
}
    """.trimIndent())
    
    return sampleFile
}