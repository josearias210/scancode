buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Use the patched Kotlin Gradle plugin with AGP's built-in Kotlin support.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")

        constraints {
            // Minimum patched versions for AGP's build-time dependencies.
            add("classpath", "com.google.guava:guava:33.7.2-jre")
            add("classpath", "org.apache.commons:commons-lang3:3.18.0")
            add("classpath", "org.apache.httpcomponents:httpclient:4.5.14")
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6")
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.85")
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.85")
            add("classpath", "org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    id("com.android.application") version "9.5.2" apply false
}
