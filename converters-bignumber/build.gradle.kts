plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    android {
        namespace = "com.sahsenvar.kmapper.bignumber"
        compileSdk = 36
        minSdk = 30
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.ionspin.bignum)
        }
        // Shared source set: java.math converters written once for both jvm and android.
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
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.sahsenvar", "kmapper-converters-bignumber", version.toString())
    pom {
        name.set("kmap converters-bignumber")
        description.set("KMP-friendly compile-time object mapper (KSP). Converters-bignumber module: ionspin BigDecimal/BigInteger (common) and java.math (jvm/android) scalar converters.")
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
