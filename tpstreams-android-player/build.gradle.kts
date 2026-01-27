plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tpstreams.player"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    api(libs.material)

    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.exoplayer.dash)
    api(libs.androidx.media3.exoplayer.hls)
    api(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.okhttp)
    api(libs.sentry.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}
apply(from = rootProject.file("gradle/gradle-mvn-build-packages.gradle"))
group = "com.github.testpress"
version = "1.1.7"

afterEvaluate {
    tasks.findByName("publishReleasePublicationToMavenLocal")?.let { publishTask ->
        publishTask.dependsOn("bundleReleaseAar")
    }
}