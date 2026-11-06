import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

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

android {
    namespace = "com.local.paralleleyeconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.paralleleyeconverter"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "0.1.27"

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
}
