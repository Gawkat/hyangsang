plugins {
    kotlin("jvm")
    // Lets the app's lint check this module too. Lint skips its API level checks on plain Kotlin
    // modules, so it won't catch calls missing from the app's minSdk.
    alias(libs.plugins.android.lint)
}

dependencies {
    testImplementation(libs.junit)
}
