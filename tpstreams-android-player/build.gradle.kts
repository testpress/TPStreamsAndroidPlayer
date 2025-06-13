plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        ignoreWarnings = true
        quiet = true
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    api(libs.material)

    // Media3 dependencies
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.exoplayer.dash)
    api(libs.androidx.media3.ui)
    api(libs.androidx.media3.exoplayer.hls)
    
    // Additional Media3 dependencies for download functionality
    implementation("androidx.media3:media3-database:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-datasource:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-common:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-exoplayer:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-exoplayer-workmanager:${libs.versions.media3.get()}")
    
    // Compose dependencies for UI components
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.3")
    
    implementation(libs.kotlinx.coroutines.android)
    api(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    afterEvaluate {
        publishing {
            publications {
                create<MavenPublication>("release") {
                    groupId = "com.tpstreams"
                    artifactId = "tpstreams-player"
                    version = "1.0.2"

                    from(components["release"])
                }
            }
            repositories {
                mavenLocal()
            }
        }
    }
}
