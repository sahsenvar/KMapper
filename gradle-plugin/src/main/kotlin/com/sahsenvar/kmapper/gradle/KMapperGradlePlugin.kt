package com.sahsenvar.kmapper.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/** The `KMapper { … }` extension. */
abstract class KMapperExtension {
    /** Module-wide return wrapper for mappings declared with `wrapper = KMapperWrapper.Default::class`. */
    abstract val wrapper: Property<KMapperWrapper>
}

/**
 * `id("io.github.sahsenvar.kmapper")` — registers the `KMapper { … }` extension and forwards it to
 * the KMapper KSP processor as processor options (`kmapper.wrapper`).
 *
 * Talks to KSP reflectively (the `ksp` extension's `arg(String, Provider<String>)`), so it works
 * whichever build-script classloader the KSP plugin was loaded in, with no pinned KSP version.
 * Without this plugin the same setting is `ksp { arg("kmapper.wrapper", "KtResult") }`.
 */
class KMapperGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, KMapperExtension::class.java)
        extension.wrapper.convention(KMapperWrapper.None)

        project.pluginManager.withPlugin(KSP_PLUGIN_ID) {
            val kspExtension = project.extensions.getByName(KSP_EXTENSION_NAME)
            val argument = kspExtension.javaClass.getMethod("arg", String::class.java, Provider::class.java)
            argument.invoke(kspExtension, WRAPPER_OPTION, extension.wrapper.map { it.optionValue })
        }

        project.afterEvaluate {
            if (!project.pluginManager.hasPlugin(KSP_PLUGIN_ID)) {
                project.logger.warn(
                    "KMapper: '$PLUGIN_ID' is applied to ${project.path} without '$KSP_PLUGIN_ID' — " +
                        "the KMapper { } settings have no processor to reach.",
                )
            }
        }
    }

    companion object {
        const val PLUGIN_ID = "io.github.sahsenvar.kmapper"
        const val EXTENSION_NAME = "KMapper"
        const val WRAPPER_OPTION = "kmapper.wrapper"
        private const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
        private const val KSP_EXTENSION_NAME = "ksp"
    }
}
