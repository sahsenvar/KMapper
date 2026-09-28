package com.sahsenvar.kmapper.gradle

import com.google.devtools.ksp.gradle.KspExtension
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder

class KMapperGradlePluginTest :
    FunSpec({

        // `also`, not `apply`: Project declares its own member `apply(...)` that would win.
        fun kotlinProjectWithKsp(): Project = ProjectBuilder.builder().build().also { project ->
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
            project.pluginManager.apply("com.google.devtools.ksp")
        }

        fun Project.kspWrapperOption(): String? {
            (this as ProjectInternal).evaluate()
            return extensions.getByType(KspExtension::class.java).arguments[KMapperGradlePlugin.WRAPPER_OPTION]
        }

        test("an unset wrapper forwards None to the processor") {
            val project = kotlinProjectWithKsp()
            project.pluginManager.apply(KMapperGradlePlugin.PLUGIN_ID)

            project.kspWrapperOption() shouldBe "None"
        }

        context("each wrapper is forwarded as the kmapper.wrapper KSP option") {
            withData(
                nameFn = { (wrapper, _) -> wrapper.toString() },
                KMapperWrapper.None to "None",
                KMapperWrapper.KtResult to "KtResult",
                KMapperWrapper.Flow to "Flow",
                KMapperWrapper.Custom("com.example.mapping.OutcomeWrapper") to "com.example.mapping.OutcomeWrapper",
            ) { (wrapper, expectedOption) ->
                val project = kotlinProjectWithKsp()
                project.pluginManager.apply(KMapperGradlePlugin.PLUGIN_ID)
                project.extensions.getByType(KMapperExtension::class.java).wrapper.set(wrapper)

                project.kspWrapperOption() shouldBe expectedOption
            }
        }

        test("applying KMapper before KSP still reaches the ksp extension") {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(KMapperGradlePlugin.PLUGIN_ID)
            project.extensions.getByType(KMapperExtension::class.java).wrapper.set(KMapperWrapper.KtResult)
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
            project.pluginManager.apply("com.google.devtools.ksp")

            project.kspWrapperOption() shouldBe "KtResult"
        }

        test("the extension is registered under the name KMapper") {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(KMapperGradlePlugin.PLUGIN_ID)

            (project.extensions.findByName("KMapper") is KMapperExtension) shouldBe true
        }

        test("a blank custom wrapper name is rejected") {
            shouldThrow<IllegalArgumentException> { KMapperWrapper.Custom(" ") }
        }
    })
