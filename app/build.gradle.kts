import io.sentry.android.gradle.instrumentation.logcat.LogcatLevel
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jmailen.kotlinter")
    id("io.sentry.android.gradle") version "4.13.0"
    id("app.cash.sqldelight") version "2.0.2"
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

val composeBomVersion = "2024.10.01"
val accompanistVersion = "0.36.0"
val navVersion = "2.8.4"
val hiltVersion = "2.52"
val glideVersion = "4.16.0"
val ktorVersion = "3.0.1"
val aboutlibrariesVersion = "11.2.3"
val media3Version = "1.4.1"
val livekitVersion = "2.2.0"
val material3Version = "1.4.0-alpha04"
val androidXTestVersion = "1.6.2"

fun property(fileName: String, propertyName: String, fallbackEnv: String? = null): String? {
    val propsFile = rootProject.file(fileName)
    if (propsFile.exists()) {
        val props = Properties()
        props.load(FileInputStream(propsFile))
        if (props[propertyName] != null) {
            return props[propertyName] as String?
        } else {
            logger.warn("Property '$propertyName' not found in '$fileName'. Attempting to use environment variable '$fallbackEnv'")
            if (fallbackEnv != null) {
                val env = System.getenv(fallbackEnv)
                if (env != null) {
                    return env
                } else {
                    logger.warn("Environment variable '$fallbackEnv' not found either. Returning null")
                }
            }
            return null
        }
    } else {
        logger.warn("Properties file '$fileName' not found. Attempting to use environment variable '$fallbackEnv'")
        if (fallbackEnv != null) {
            val env = System.getenv(fallbackEnv)
            if (env != null) {
                return env
            } else {
                logger.warn("Environment variable '$fallbackEnv' not found either. Returning null")
            }
        }
        return null
    }
}

// Calls property but with revoltbuild.properties as the first argument
fun buildproperty(propertyName: String, fallbackEnv: String? = null): String? {
    return property("revoltbuild.properties", propertyName, fallbackEnv)
}

android {
    compileSdk = 35

    defaultConfig {
        applicationId = "chat.revolt"
        minSdk = 24
        targetSdk = 35
        versionCode = Integer.parseInt("001_002_005".replace("_", ""), 10)
        versionName = "1.2.5-beta+gp20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField(
                "String",
                "SENTRY_DSN",
                "\"${buildproperty("sentry.dsn", "RVX_SENTRY_DSN")}\""
            )
            buildConfigField(
                "String",
                "FLAVOUR_ID",
                "\"${buildproperty("build.flavour_id", "RVX_BUILD_FLAVOUR_ID")}\""
            )
        }

        debug {
            isPseudoLocalesEnabled = true

            applicationIdSuffix = ".debug"
            versionNameSuffix = "+debug"
            resValue(
                "string",
                "app_name",
                buildproperty("build.debug.app_name", "RVX_DEBUG_APP_NAME")!!
            )

            buildConfigField(
                "String",
                "SENTRY_DSN",
                "\"${buildproperty("sentry.dsn", "RVX_SENTRY_DSN")}\""
            )
            buildConfigField(
                "String",
                "FLAVOUR_ID",
                "\"${buildproperty("build.flavour_id", "RVX_BUILD_FLAVOUR_ID")}\""
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
    namespace = "chat.revolt"
    externalNativeBuild {
        cmake {
            path(file("src/main/cpp/CMakeLists.txt"))
            version = "3.22.1"
        }
    }
    lint {
        abortOnError = false
        disable += "MissingTranslation"
    }
}

sentry {
    autoUploadProguardMapping =
        buildproperty("sentry.upload_mappings", "RVX_SENTRY_UPLOAD_MAPPINGS") == "true"

    tracingInstrumentation {
        enabled = true

        logcat {
            enabled = true
            minLevel = LogcatLevel.WARNING
        }
    }
}

dependencies {
    // Android/Kotlin Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")

    // Kotlinx - various first-party extensions for Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:$composeBomVersion")
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-util")
    implementation("androidx.compose.material3:material3:$material3Version")
    implementation("androidx.compose.material3:material3-window-size-class:$material3Version")
    implementation("androidx.compose.material:material-icons-core:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Accompanist - Jetpack Compose Extensions
    implementation("com.google.accompanist:accompanist-systemuicontroller:$accompanistVersion")
    implementation("com.google.accompanist:accompanist-permissions:$accompanistVersion")

    // KTOR - HTTP+WebSocket Library
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")

    // Screen Navigation
    implementation("androidx.navigation:navigation-compose:$navVersion")

    // Jetpack Compose Tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Hilt - Dependency Injection
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")

    // Glide - Image Loading
    implementation("com.github.bumptech.glide:glide:$glideVersion")
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")
    ksp("com.github.bumptech.glide:ksp:$glideVersion")

    // AboutLibraries - automated OSS library attribution
    implementation("com.mikepenz:aboutlibraries-compose:$aboutlibrariesVersion")

    // Sentry - crash reporting
    implementation("io.sentry:sentry-android:7.18.0")
    implementation("io.sentry:sentry-compose-android:7.18.0")

    // Other AndroidX libraries - used for various things and never seem to have a consistent version
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core-splashscreen:1.2.0-alpha02")

    // Libraries used for legacy View-based UI
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // hCaptcha - captcha provider
    implementation("com.github.hcaptcha:hcaptcha-android-sdk:3.8.1")

    // JDK Desugaring - polyfill for new Java APIs
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    // AndroidX Media3 w/ ExoPlayer
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Compose libraries
    implementation("me.saket.telephoto:zoomable-image:1.0.0-alpha02")
    implementation("me.saket.telephoto:zoomable-image-glide:1.0.0-alpha02")

    // Persistence
    implementation("app.cash.sqldelight:android-driver:2.0.2")
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Markup
    implementation("org.jetbrains:markdown:0.7.3")
    implementation("dev.snipme:highlights:1.0.0")

    // Livekit
    // FIXME temporarily not included, re-add when realtime media is to be implemented
    // implementation "io.livekit:livekit-android:$livekit_version"

    // Firebase - Cloud Messaging
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Shimmer - loading animations
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.1")

    // Chucker - HTTP inspector
    debugImplementation("com.github.chuckerteam.chucker:library:4.0.0")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.0.0")

    // Testing
    androidTestImplementation("androidx.test:runner:$androidXTestVersion")
    androidTestImplementation("androidx.test:rules:$androidXTestVersion")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("chat.revolt.persistence")
        }
    }
}