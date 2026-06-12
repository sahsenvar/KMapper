plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":annotations"))
    implementation(project(":converters-immutable"))
    implementation(project(":converters-arrow"))
    implementation(project(":validators"))
    ksp(project(":processor"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.arrow.core)
}

// Runs EVERY example in the gallery in learning-path order:
//   ./gradlew sample:runSample
// (To run a single example, hit the ▶ next to its `main` in the IDE — each file has one.)
tasks.register<JavaExec>("runSample") {
    group = "samples"
    description = "Runs the whole sample gallery (sample.GalleryRunner)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("sample.GalleryRunnerKt")
}
