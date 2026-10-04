plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.sqlite.jdbc)
    implementation("com.google.code.gson:gson:2.14.0")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("dev.kettu.hyangsang.tools.MainKt")
}

// The generator's paths are relative to the repository root
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

// Builds the lookup evaluation sets from the generated dictionary, see EvalSetGenerator.kt
tasks.register<JavaExec>("generateEvalSet") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.kettu.hyangsang.tools.EvalSetGeneratorKt")
    workingDir = rootDir
}
