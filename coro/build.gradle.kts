dependencies {
    testAnnotationProcessor(project(":ap"))
}

tasks {
    test {
        useJUnitPlatform()
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}