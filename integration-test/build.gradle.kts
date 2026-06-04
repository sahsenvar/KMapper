plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.sahsenvar.kmapper.itest"
        compileSdk = 36
        minSdk = 30
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":converters-immutable"))
            implementation(project(":converters-arrow"))
            implementation(project(":converters-datetime"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":processor"))
    add("kspJvm", project(":processor"))
    add("kspIosArm64", project(":processor"))
    add("kspIosSimulatorArm64", project(":processor"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
