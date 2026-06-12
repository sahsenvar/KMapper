plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.bcv)
    alias(libs.plugins.dokka)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

allprojects {
    group = "io.github.sahsenvar"
    version = "2.0.0"
}

@OptIn(kotlinx.validation.ExperimentalBCVApi::class)
apiValidation {
    ignoredProjects += listOf("sample", "integration-test")
    klib {
        enabled = true
    }
}

// ---------------------------------------------------------------------------
// Spotless — formatting & lint
// ---------------------------------------------------------------------------
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint().editorConfigOverride(
            mapOf(
                // Intentional file-name ≠ top-level declaration (e.g. NonEmptyListConverters.kt).
                // Renaming published source files is a breaking change; suppress filename rule.
                "ktlint_standard_filename" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**/*.gradle.kts")
        ktlint()
    }
}

// ---------------------------------------------------------------------------
// Kover — coverage aggregation (root-level aggregate report)
// ---------------------------------------------------------------------------
dependencies {
    // Published modules for coverage aggregation
    kover(project(":core"))
    kover(project(":annotations"))
    kover(project(":processor"))
    kover(project(":converters-immutable"))
    kover(project(":converters-arrow"))
    kover(project(":converters-datetime"))
    kover(project(":converters-bignumber"))
    kover(project(":converters-uuid"))
    kover(project(":converters-okio"))
    kover(project(":converters-uri"))
    kover(project(":validators"))

    // Dokka aggregation — published modules
    dokka(project(":core"))
    dokka(project(":annotations"))
    dokka(project(":processor"))
    dokka(project(":converters-immutable"))
    dokka(project(":converters-arrow"))
    dokka(project(":converters-datetime"))
    dokka(project(":converters-bignumber"))
    dokka(project(":converters-uuid"))
    dokka(project(":converters-okio"))
    dokka(project(":converters-uri"))
    dokka(project(":validators"))
}

// ---------------------------------------------------------------------------
// Dokka — multi-module HTML site configuration
// ---------------------------------------------------------------------------
dokka {
    dokkaPublications.html {
        moduleName.set("KMapper")
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}
