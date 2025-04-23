repositories {
    mavenCentral()
}

dependencies {
    testAnnotationProcessor(project(":ap"))
}

tasks.test {
    useJUnitPlatform()
}