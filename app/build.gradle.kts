import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Historical releases used this project's original debug certificate.
// Never fall back to, copy, or replace the user's global debug.keystore.
val privateSigningFile = rootProject.file(".local/private/signing.properties")
check(privateSigningFile.isFile) { "Restore .local/private/signing.properties before building; see docs/development.md" }
val privateSigning = Properties().apply {
    privateSigningFile.inputStream().use { load(it) }
}
val originalDebugKey = rootProject.file(".local/private/debug.keystore")
check(originalDebugKey.isFile) { "Missing original project debug key in .local/private" }

val qnnNotices by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

abstract class CollectQnnNotices : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeArchive: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun collect() {
        files.sync {
            from(archives.zipTree(runtimeArchive.singleFile)) {
                include("LICENSE.pdf", "NOTICE.txt")
                into("third-party/qnn")
            }
            into(assetsDirectory)
        }
    }
}

val collectQnnNotices by tasks.registering(CollectQnnNotices::class) {
    // Preserve the notices from the same Maven AAR that supplies the bundled GPU runtime.
    runtimeArchive.from(qnnNotices)
    assetsDirectory.set(layout.buildDirectory.dir("generated/qnn-assets"))
}

android {
    namespace = "com.local.paralleleyeconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.paralleleyeconverter"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "0.1.30"

    }

    buildFeatures {
        buildConfig = false
    }

    signingConfigs {
        getByName("debug") {
            storeFile = originalDebugKey
            storePassword = requireNotNull(privateSigning.getProperty("storePassword"))
            keyAlias = requireNotNull(privateSigning.getProperty("keyAlias"))
            keyPassword = requireNotNull(privateSigning.getProperty("keyPassword"))
        }
    }

    androidResources {
        noCompress += "tflite"
        noCompress += "onnx"
    }

    packaging {
        jniLibs {
            // QNN opens its GPU backend from the installed native library directory.
            useLegacyPackaging = true
            excludes += setOf("**/libQnnDsp*.so", "**/libQnnHtp*.so")
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(collectQnnNotices, CollectQnnNotices::assetsDirectory)
    }
}

android.applicationVariants.all {
    outputs.all {
        (this as BaseVariantOutputImpl).outputFileName =
            "ParallelEyeConverter-v${versionName}-${name}.apk"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0")
    qnnNotices("com.qualcomm.qti:qnn-runtime:2.42.0@aar")
}
