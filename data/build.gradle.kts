plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

android {
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    namespace = "com.example.manga_readerver2.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    sourceSets {
        named("main") {
            java.srcDir("build/generated/sqldelight/code/Database/main")
        }
        named("debug") {
            java.srcDir("build/generated/sqldelight/code/Database/debug")
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.example.mangareaderver2.database")
            dialect(libs.sqldelight.sqliteDialect338)
            generateAsync.set(false)
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    api(libs.sqldelight.android.driver)
    api(libs.sqldelight.coroutines)
    api(libs.sqldelight.async)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)
}













