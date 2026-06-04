plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.publish)
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
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-core", version.toString())
    pom {
        name.set("kmap core")
        description.set("KMP-friendly compile-time object mapper (KSP). Core module: annotations, MappingException, MapTypeConverter registry, built-in converters, MappableEnum.")
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
