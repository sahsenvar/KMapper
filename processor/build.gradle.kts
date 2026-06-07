import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(libs.compile.testing.core)
    testImplementation(libs.compile.testing.ksp)
    testImplementation(libs.kotlinx.collections.immutable)
}

tasks.test { useJUnitPlatform() }

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-processor", version.toString())
    pom {
        name.set("KMapper processor")
        description.set("KMP-friendly compile-time object mapper (KSP). Processor module: KSP code generator for @MapTo/@MapFrom → toX() extension functions.")
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
