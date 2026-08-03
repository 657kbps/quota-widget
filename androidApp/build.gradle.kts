import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.kuyermqi.quotawidget"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kuyermqi.quotawidget"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 10
        versionName = (findProperty("versionName") as String?) ?: "1.4.1"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
    signingConfigs {
        val storeFilePath = System.getenv("RELEASE_STORE_FILE")
        val storePassword = System.getenv("RELEASE_STORE_PASSWORD")
        val keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        val keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        if (!storeFilePath.isNullOrBlank() &&
            !storePassword.isNullOrBlank() &&
            !keyAlias.isNullOrBlank() &&
            !keyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.findByName("release")
            val allowDebugReleaseSigning =
                (findProperty("allowDebugReleaseSigning") as String?) == "true"
            signingConfig = releaseSigning
                ?: if (allowDebugReleaseSigning) {
                    signingConfigs.getByName("debug")
                } else {
                    null
                }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any { task ->
        val name = task.name
        name.contains("Release", ignoreCase = true) &&
            (name.startsWith("assemble") ||
                name.startsWith("bundle") ||
                name.startsWith("package") ||
                name.startsWith("sign") ||
                name.startsWith("install"))
    }
    if (buildingRelease) {
        val allowDebugReleaseSigning =
            (findProperty("allowDebugReleaseSigning") as String?) == "true"
        val releaseSigning = android.signingConfigs.findByName("release")
        val releaseType = android.buildTypes.getByName("release")
        val ok = releaseSigning != null && releaseType.signingConfig == releaseSigning
        val okDebugFallback =
            allowDebugReleaseSigning &&
                releaseType.signingConfig == android.signingConfigs.getByName("debug")
        if (!ok && !okDebugFallback) {
            error(
                "Release signing is not configured. Set RELEASE_STORE_FILE, " +
                    "RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD.",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(projects.shared)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.material.kolor)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
