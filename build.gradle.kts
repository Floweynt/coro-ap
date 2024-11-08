plugins {
    id("java")
}

group = "com.floweytf"

val ver = "1.0-SNAPSHOT"
version = ver

allprojects {
    version = ver
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.jetbrains:annotations:24.0.0")
    }
}