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
        versionCode = 13
        versionName = "0.1.12"

    }

    buildFeatures {
        buildConfig = false
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
