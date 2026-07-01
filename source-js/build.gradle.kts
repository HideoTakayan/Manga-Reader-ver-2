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
    namespace = "com.example.manga_readerver2.source_js"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.quickjs)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logcat)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    
    testImplementation(libs.junit)
}












