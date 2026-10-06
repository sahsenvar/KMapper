import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

// The plugin's types are read by the consumer's build scripts, which Gradle compiles with its
// EMBEDDED Kotlin — keep the metadata and bytecode conservative so any Gradle 8.x/9.x can load it.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

gradlePlugin {
    website.set("https://github.com/sahsenvar/KMapper")
    vcsUrl.set("https://github.com/sahsenvar/KMapper")
    plugins {
        create("kmapper") {
            id = "io.github.sahsenvar.kmapper"
            implementationClass = "com.sahsenvar.kmapper.gradle.KMapperGradlePlugin"
            displayName = "KMapper"
            description = "Module-wide KMapper settings (e.g. the default return wrapper) passed to the KMapper KSP processor."
        }
    }
}

dependencies {
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlin.gradle.plugin)
    testImplementation(libs.ksp.gradle.plugin)
}

tasks.test { useJUnitPlatform() }

mavenPublishing {
    configure(GradlePlugin(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-gradle-plugin", version.toString())
    pom {
        name.set("KMapper Gradle plugin")
        description.set(
            "Gradle plugin for KMapper: `KMapper { wrapper = KMapperWrapper.KtResult }` sets the module-wide return wrapper of generated mappers.",
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
