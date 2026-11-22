plugins {
    id("com.android.application")
}

android {
    namespace = "com.scancode.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.scancode.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    constraints {
        // CameraX pulls in Guava Android through its video dependencies.
        implementation("com.google.guava:guava:33.8.0-android") {
            because("GHSA-xxph-c9ww-hj94 is fixed in Guava 33.7.2 and later")
        }

        // AGP resolves the Lint tool on a separate classpath from the buildscript.
        add("androidLintTool", "com.google.guava:guava:33.8.0-jre")
        add("androidLintTool", "org.apache.commons:commons-lang3:3.22.0")
        add("androidLintTool", "org.apache.httpcomponents:httpclient:4.5.14")
        add("androidLintTool", "org.bouncycastle:bcpkix-jdk18on:1.87")
        add("androidLintTool", "org.bouncycastle:bcprov-jdk18on:1.87")
    }

    val camerax = "1.7.0"
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-mlkit-vision:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
