rootProject.name = "kmap"

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

include(":core", ":processor", ":converters-compose", ":converters-arrow", ":sample")
