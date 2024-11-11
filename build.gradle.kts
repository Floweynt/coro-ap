plugins {
    id("java")
}

group = "com.floweytf.coro"

val ver = "1.0-SNAPSHOT"
version = ver

allprojects {
    version = ver
    group = "com.floweytf.coro"
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.jetbrains:annotations:24.0.0")
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }
}