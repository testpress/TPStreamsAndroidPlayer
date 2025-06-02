import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.31.0"
}

android {
    namespace = "com.tpstreams"
    compileSdk = 35
    version = "0.0.1"
    group = "com.tpstreams"

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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    api(libs.material)

    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.exoplayer.dash)
    api(libs.androidx.media3.ui)
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
                    version = "1.0.1"

                    from(components["release"])
                }
            }
            repositories {
                mavenLocal()
            }
        }
    }
}

mavenPublishing {

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "tpstreams-player", version.toString())

    pom {
        name.set("TPStreamsAndroidPlayer")
        description.set("An android sdk for TPStreams Player")
        url.set("https://github.com/testpress/TPStreamsAndroidPlayer")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/testpress/TPStreamsAndroidPlayer")
            connection.set("scm:git:git://github.com:testpress/TPStreamsAndroidPlayer.git")
            developerConnection.set("scm:git:ssh://github.com:testpress/TPStreamsAndroidPlayer.git")
        }
    }
}