plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    // Lets the app's lint check this module too. Lint skips its API level checks on plain Kotlin
    // modules, so it won't catch calls missing from the app's minSdk.
    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
