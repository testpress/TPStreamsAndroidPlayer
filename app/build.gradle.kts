plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val isTestApk = project.hasProperty("isTestApk")
val sdkVersion = providers.gradleProperty("VERSION_NAME").getOrElse("1.0")

android {
    namespace = "com.tpstreams.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tpstreams.player"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["launcherActivity"] = if (isTestApk) {
            ".TestPlayerActivity"
        } else {
            ".MainActivity"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }

    if (isTestApk) {
        androidComponents {
            onVariants { variant ->
                @Suppress("UnstableApiUsage")
                variant.outputs.forEach { output ->
                    (output as com.android.build.api.variant.impl.VariantOutputImpl)
                        .outputFileName.set("${sdkVersion}.apk")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.media3.ui)
    implementation(project(":tpstreams-android-player"))
    implementation(libs.sentry.android)
    implementation(libs.material)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
