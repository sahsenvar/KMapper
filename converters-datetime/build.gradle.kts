import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.sahsenvar.kmapper.datetime"
        compileSdk = 36
        minSdk = 30
    }
    // JVM/Android only: the 2.0 audit moved the kotlinx-datetime converters into core
    // built-ins, so this module carries java.time converters and bridges exclusively —
    // there is nothing to ship for native targets (and an empty iOS klib breaks publishing).
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.datetime)
        }
        // Shared source set: java.time converters written once for both jvm and android.
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.property)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-converters-datetime", version.toString())
    pom {
        name.set("KMapper converters-datetime")
        description.set(
            "KMP-friendly compile-time object mapper (KSP). Converters-datetime module: kotlinx-datetime (common) and java.time (jvm/android) scalar converters + kotlinx↔java bridges.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/sahsenvar/KMapper")
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
            url.set("https://github.com/sahsenvar/KMapper")
            connection.set("scm:git:git://github.com/sahsenvar/KMapper.git")
            developerConnection.set("scm:git:ssh://git@github.com/sahsenvar/KMapper.git")
        }
    }
}
