plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.bcv)
    alias(libs.plugins.dokka) apply false
}

allprojects {
    group = "io.github.sahsenvar"
    version = "1.0.0"
}

@OptIn(kotlinx.validation.ExperimentalBCVApi::class)
apiValidation {
    ignoredProjects += listOf("sample", "integration-test")
    klib {
        enabled = true
    }
}
