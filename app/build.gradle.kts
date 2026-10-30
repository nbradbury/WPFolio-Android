import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun localProp(key: String): String = localProps.getProperty(key).orEmpty()

if (localProp("WPCOM_CLIENT_ID").isBlank()) {
    logger.warn("WPCOM_CLIENT_ID is not set in local.properties; WordPress.com sign-in will be disabled.")
}

android {
    namespace = "com.nbradbury.folio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nbradbury.folio"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "WPCOM_CLIENT_ID", "\"${localProp("WPCOM_CLIENT_ID")}\"")
        buildConfigField("String", "WPCOM_CLIENT_SECRET", "\"${localProp("WPCOM_CLIENT_SECRET")}\"")
        val redirectUri = localProp("WPCOM_REDIRECT_URI").ifBlank { "folio://oauth" }
        buildConfigField("String", "WPCOM_REDIRECT_URI", "\"$redirectUri\"")
        URI(redirectUri).let {
            manifestPlaceholders["oauthScheme"] = it.scheme
            manifestPlaceholders["oauthHost"] = it.host
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.palette)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
