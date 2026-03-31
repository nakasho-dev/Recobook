import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.ukky.recobook.androidApp"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("RECOBOOK_ANDROID_JKS_PATH")
            val keystorePassword = System.getenv("RECOBOOK_ANDROID_JKS_PASSWORD")
            val alias = System.getenv("RECOBOOK_ANDROID_JKS_ALIAS")
            val keyPassword = System.getenv("RECOBOOK_ANDROID_JKS_KEY_PASSWORD")

            if (listOf(keystorePath, keystorePassword, alias, keyPassword).any { it.isNullOrBlank() }) {
                throw GradleException("Signing env vars are missing.")
            }

            storeFile = file(keystorePath!!)
            storePassword = keystorePassword
            keyAlias = alias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        getByName("release") {
            // 必要なら有効化
            // isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    defaultConfig {
        minSdk = 23
        targetSdk = 36

        applicationId = "org.ukky.recobook.androidApp"
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(libs.androidx.activityCompose)
}
