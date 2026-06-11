rootProject.name = "KMapper"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":core",
    ":annotations",
    ":processor",
    ":converters-immutable",
    ":converters-arrow",
    ":converters-datetime",
    ":converters-bignumber",
    ":converters-uuid",
    ":converters-okio",
    ":converters-uri",
    ":validators",
    ":sample",
    ":integration-test",
)
