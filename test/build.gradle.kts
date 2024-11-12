repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(project(":coro"))
    annotationProcessor(project(":ap"))
}

tasks.test {
    useJUnitPlatform()
}