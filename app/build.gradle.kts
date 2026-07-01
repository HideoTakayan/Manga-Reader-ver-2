plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    namespace = "com.example.manga_readerver2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.manga_readerver2"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)



    // OkHttp (Network)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coil (Image Loading)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // DataStore (Preferences - thay SharedPreferences)
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Splashscreen
    implementation(libs.androidx.core.splashscreen)

    // DocumentFile (SAF - Import Folder)
    implementation(libs.androidx.documentfile)

    // Archive (đọc CBZ/ZIP)
    implementation(libs.apache.commons.compress)

    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Domain & Data modules
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core"))
    implementation(project(":source-api"))
    implementation(project(":source-dex"))
    implementation(project(":source-js"))

    // Voyager (Navigation)
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.screenmodel)
    implementation(libs.voyager.tab.navigator)
    implementation(libs.voyager.transitions)

    // Injekt (DI)
    implementation(libs.injekt)

    // QuickJS (JS Engine)
    implementation(libs.quickjs)

    // Logcat
    implementation(libs.logcat)
    implementation(libs.rxjava)

    // Serialization
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.async)
    implementation(libs.kotlinx.serialization.json)

    // Conscrypt (Better TLS)
    implementation(libs.conscrypt)
    // Subsampling (Mihon style)
    implementation(libs.subsampling.scale.image.view)

    // Jsoup (HTML processing for EPUB)
    implementation(libs.jsoup)
    implementation(libs.androidx.viewpager2)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}












