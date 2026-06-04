plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":converters-immutable"))
    implementation(project(":converters-arrow"))
    ksp(project(":processor"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.arrow.core)
}
