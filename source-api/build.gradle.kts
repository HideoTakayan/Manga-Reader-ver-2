plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    namespace = "com.example.manga_readerver2.source_api"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    
}

kotlin {
    
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.injekt)
    implementation("androidx.preference:preference-ktx:1.2.1")
}
