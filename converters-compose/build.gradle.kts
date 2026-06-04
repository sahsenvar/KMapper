plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    `maven-publish`
}

kotlin {
    android {
        namespace = "com.sahsenvar.kmapper.compose"
        compileSdk = 36
        minSdk = 30
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.collections.immutable)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":processor"))
}

// Standard KMP-KSP wiring: every Kotlin compile task (except kspCommonMainKotlinMetadata itself)
// depends on kspCommonMainKotlinMetadata so generated sources are available during compilation.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
