@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Wrap-direction source gating in TypeMatcher. The wrap contract is
 * `fun <T> wrap(source: List<T>): W<T>` — its source must be stdlib List-shaped
 * (kotlin.collections.List / MutableList). A non-List collection source (Set, another
 * wrapped type) into a wrapped target is a GUIDED processor error suggesting a List source
 * or an explicit `.toList()` conversion — never a raw generated-code compile failure.
 *
 * A registered wrapper type mapped to ITSELF with the same element type is a plain Direct
 * passthrough — no pointless unwrap/rewrap round-trip through the wrapper object.
 */
class WrappedCollectionGatingTest :
    BehaviorSpec({

        fun gatingSource(
            sourceFieldType: String,
            targetFieldType: String,
        ) = SourceFile.kotlin(
            "WrapGatingModels.kt",
            """
            import com.sahsenvar.kmapper.annotations.CollectionWrapper
            import com.sahsenvar.kmapper.annotations.KMapperConfig
            import com.sahsenvar.kmapper.annotations.MapTo
            import kotlinx.collections.immutable.PersistentList
            import kotlinx.collections.immutable.toPersistentList

            @CollectionWrapper(forType = PersistentList::class)
            object PersistentListWrapper {
                fun <T> wrap(source: List<T>): PersistentList<T> = source.toPersistentList()
            }

            @KMapperConfig(wrappers = [PersistentListWrapper::class])
            object MappingConfig

            data class DomainModel(val tags: $targetFieldType)

            @MapTo(DomainModel::class)
            data class DataModel(val tags: $sourceFieldType)
            """.trimIndent(),
        )

        given("a Set source mapped into a registered PersistentList target") {
            `when`("Set<String> -> PersistentList<String> is compiled") {
                val (compilationResult, _) =
                    compile(gatingSource(sourceFieldType = "Set<String>", targetFieldType = "PersistentList<String>"))

                then("compilation fails with a GUIDED processor diagnostic, not a raw codegen type error") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                }

                then("the error suggests a List source or an explicit .toList() conversion") {
                    compilationResult.messages shouldContain ".toList()"
                }
            }
        }

        given("a registered wrapper type mapped to ITSELF with the same element type") {
            `when`("PersistentList<String> -> PersistentList<String> is compiled") {
                val (compilationResult, compilation) =
                    compile(
                        gatingSource(
                            sourceFieldType = "PersistentList<String>",
                            targetFieldType = "PersistentList<String>",
                        ),
                    )

                then("compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("the mapping is a Direct passthrough — no wrap call") {
                    val generated = compilation.generatedFile("DataModelMappers.kt")
                    generated shouldNotContain "PersistentListWrapper.wrap("
                    generated shouldContain "tags = tags"
                }
            }
        }
    })
