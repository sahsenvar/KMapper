plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    group = "com.sahsenvar.kmapper"
    version = "0.1.0-SNAPSHOT"
}
