buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Use the patched Kotlin Gradle plugin with AGP's built-in Kotlin support.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.2" apply false
}
