import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.local.paralleleyeconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.paralleleyeconverter"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"

    }

    buildFeatures {
        buildConfig = false
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

dependencies {}
