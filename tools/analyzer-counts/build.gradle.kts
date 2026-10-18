plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":analyzer"))
    implementation(libs.sqlite.jdbc)
}

application {
    mainClass.set("dev.kettu.hyangsang.tools.counts.MainKt")
}

// The generator's paths are relative to the repository root
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
