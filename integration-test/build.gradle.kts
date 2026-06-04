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
    // kspCommonMainMetadata: the intended primary path for KMP consumers.
    // ARCHITECTURAL FINDING: In KSP2's multi-pass kspCommonMainMetadata, the processor
    // invocation for the consumer module (invocation 3) cannot see @CollectionWrapper
    // or @CollectionWrapperDescriptor annotations from dependency project modules via
    // getSymbolsWithAnnotation or getDeclarationsFromPackage. All three APIs return 0.
    // This is a KSP2 KMP isolation bug: each module's processor invocation has a
    // resolver scope limited to that module's own sources only.
    // Result: the metadata mapper is broken (missing roles field), build fails.
    // This line is intentionally commented out until KSP2 fixes cross-module symbol visibility.
    // add("kspCommonMainMetadata", project(":processor"))

    // kspJvm: single invocation with full JVM classpath — wrapper discovery works.
    // The JVM jar of converters-arrow/converters-immutable contains compiled descriptor
    // .class files which getDeclarationsFromPackage finds correctly in a JVM KSP run.
    add("kspJvm", project(":processor"))
}

// Wire kspJvm output to compile before all Kotlin compilation tasks.
// Note: without kspCommonMainMetadata, iOS targets cannot get the generated mappers.
// The integration-test therefore only validates the JVM end-to-end pipeline.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspKotlinJvm")
    }
}
