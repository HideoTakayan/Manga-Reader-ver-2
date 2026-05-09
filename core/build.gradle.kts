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
    namespace = "com.example.manga_readerver2.core"
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
    implementation(project(":source-api"))
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.logcat)
    implementation(libs.injekt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.apache.commons.compress)
}












