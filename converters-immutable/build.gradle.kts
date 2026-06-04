plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    android {
        namespace = "com.sahsenvar.kmapper.immutable"
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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":processor"))
    // Also run KSP for JVM target so @CollectionWrapperDescriptor objects are compiled into the
    // JVM jar and discoverable by consumers via resolver.getDeclarationsFromPackage.
    add("kspJvm", project(":processor"))
}

// Standard KMP-KSP wiring: every Kotlin compile task (except the KSP metadata task itself)
// depends on kspCommonMainKotlinMetadata so generated sources are available during compilation.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-converters-immutable", version.toString())
    pom {
        name.set("kmap converters-immutable")
        description.set("KMP-friendly compile-time object mapper (KSP). Converters-immutable module: List → PersistentList/ImmutableList/ImmutableSet collection wrappers.")
        inceptionYear.set("2026")
        url.set("https://github.com/sahsenvar/kmap")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("sahsenvar")
                name.set("Şahan Şenvar")
                url.set("https://github.com/sahsenvar")
            }
        }
        scm {
            url.set("https://github.com/sahsenvar/kmap")
            connection.set("scm:git:git://github.com/sahsenvar/kmap.git")
            developerConnection.set("scm:git:ssh://git@github.com/sahsenvar/kmap.git")
        }
    }
}
