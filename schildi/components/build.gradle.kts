plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "chat.schildi.components"
}

dependencies {
    implementation(projects.schildi.lib)
    implementation(projects.schildi.theme)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.uiStrings)
    implementation(libs.colorpicker)
}
