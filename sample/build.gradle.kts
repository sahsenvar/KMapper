plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":converters-compose"))
    ksp(project(":processor"))
    implementation(libs.kotlinx.collections.immutable)
}
