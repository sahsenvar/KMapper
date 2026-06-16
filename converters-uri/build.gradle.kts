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
        namespace = "com.sahsenvar.kmapper.uri"
        compileSdk = 36
        minSdk = 26
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }

        // Shared iOS source set: NSURL converters written once for both iOS targets.
        val iosMain by creating { dependsOn(commonMain.get()) }
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)
        val iosTest by creating { dependsOn(commonTest.get()) }
        iosArm64Test.get().dependsOn(iosTest)
        iosSimulatorArm64Test.get().dependsOn(iosTest)

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions)
        }
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-converters-uri", version.toString())
    pom {
        name.set("KMapper converters-uri")
        description.set(
            "KMP-friendly compile-time object mapper (KSP). Converters-uri module: platform-specific URI converters (java.net.URI, android.net.Uri, NSURL).",
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
        scm { url.set("https://github.com/sahsenvar/KMapper") }
    }
}
