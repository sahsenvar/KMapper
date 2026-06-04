plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(libs.compile.testing.core)
    testImplementation(libs.compile.testing.ksp)
}

tasks.test { useJUnitPlatform() }
