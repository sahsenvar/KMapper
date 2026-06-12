@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Built-in conversions ride the ladder seams (old world: a path-less convertOrFail wrapper;
 * new world: the path-carrying seam with type literals and a converter-object lambda).
 */
class ConversionWrapTest :
    BehaviorSpec({

        given("a built-in String to Int conversion on a hard landing site") {
            val source =
                SourceFile.kotlin(
                    "M.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    data class CountDomainModel(val n: Int)
                    @MapTo(CountDomainModel::class)
                    data class CountDataModel(val n: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "CountDataModelMappers.kt")

                then("the conversion is wrapped in the path-carrying convertOrFail seam") {
                    generated shouldContain "convertOrFail(\"n\", \"kotlin.String\", \"kotlin.Int\")"
                }

                then("the converter OBJECT is called — never an inlined ad-hoc conversion") {
                    generated shouldContain "IntStringConverter.convertFrom(it)"
                }
            }
        }
    })
