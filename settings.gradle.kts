rootProject.name = "kmap"

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

include(":core", ":processor", ":converters-immutable", ":converters-arrow", ":converters-datetime", ":converters-bignumber", ":converters-uuid", ":sample", ":integration-test")
