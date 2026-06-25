plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    namespace = "com.example.manga_readerver2.source_dex"
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
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.logcat)
    implementation(libs.injekt)
    implementation(libs.kotlinx.coroutines.android)
}












