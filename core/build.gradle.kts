plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

kotlin {
    android {
        namespace = "com.sahsenvar.kmapper.core"
        compileSdk = 36
        minSdk = 30
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
