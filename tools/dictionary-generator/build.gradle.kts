plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.sqlite.jdbc)
    implementation("com.google.code.gson:gson:2.10.1")
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
