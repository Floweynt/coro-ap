dependencies {
    implementation(libs.bundles.asm)
    implementation(libs.fastutil)
}

tasks.compileJava {
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
    options.compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}
