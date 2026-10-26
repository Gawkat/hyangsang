plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":parser"))
    implementation(libs.jsoup)
    testImplementation(libs.junit)
}

application {
    mainClass.set("dev.kettu.hyangsang.tools.canary.MainKt")
}

// The canary's paths are relative to the repository root
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
