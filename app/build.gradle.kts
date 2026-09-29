plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val isTestApk = project.hasProperty("isTestApk")
val sdkVersion = providers.gradleProperty("VERSION_NAME").getOrElse("1.0")

android {
    namespace = "com.tpstreams.player.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tpstreams.player.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["launcherActivity"] = if (isTestApk) {
            "com.tpstreams.player.TestPlayerActivity"
        } else {
            "com.tpstreams.player.MainActivity"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (isTestApk) {
                signingConfig = signingConfigs.getByName("debug")
            }
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

if (isTestApk) {
    android.sourceSets["main"].java.srcDir("src/testApk/java")
    android.sourceSets["main"].res.srcDir("src/testApk/res")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.media3.ui)
    implementation(project(":tpstreams-android-player"))
    implementation(libs.material)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
