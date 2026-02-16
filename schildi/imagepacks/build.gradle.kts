import extension.setupDependencyInjection

plugins {
    id("io.element.android-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "chat.schildi.imagepacks"
}

setupDependencyInjection(generateNodeFactories = false)

dependencies {
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.timber)
}
