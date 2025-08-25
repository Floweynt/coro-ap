plugins {
    java
    `maven-publish`
}

group = "com.floweytf.coro"

val ver = "0.0.1-SNAPSHOT"
version = ver

allprojects {
    version = ver
    group = "com.floweytf.coro"
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.jetbrains:annotations:24.0.0")
    }

    publishing {
        repositories {
            mavenLocal()
        }

        publishing {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
        }
    }
}