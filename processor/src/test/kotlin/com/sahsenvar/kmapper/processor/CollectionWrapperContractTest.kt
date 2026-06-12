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
 * @CollectionWrapper duck-typed contract validation — partition-then-policy over wrap/unwrap
 * OVERLOADS, and type-argument LINKAGE inside [com.sahsenvar.kmapper.processor.analyzer.CollectionWrapperValidator].
 *
 * Overload policy: every function named wrap/unwrap is individually validated. A matching
 * overload provides the direction; every non-matching same-name overload is reported as a
 * guided error quoting the expected signature — never silently skipped, regardless of
 * declaration order. When NO overload matches but candidates exist, the direction stays
 * unprovided and each candidate gets the guided error.
 *
 * Linkage policy: the List parameter's element argument AND the forType return's element
 * argument must BOTH be the function's own type parameter — `fun <T> wrap(source: List<String>):
 * PersistentList<Int>` matches the container declarations but is NOT the contract shape.
 */
class CollectionWrapperContractTest :
    BehaviorSpec({

        val wrongShapeMarker = "wrap has the wrong shape"
        val noWrapDirectionMarker = "declares no wrap"

        fun wrapperSource(wrapperBody: String) = SourceFile.kotlin(
            "WrapperContractModels.kt",
            """
                import com.sahsenvar.kmapper.annotations.CollectionWrapper
                import com.sahsenvar.kmapper.annotations.KMapperConfig
                import com.sahsenvar.kmapper.annotations.MapTo
                import kotlinx.collections.immutable.PersistentList
                import kotlinx.collections.immutable.persistentListOf
                import kotlinx.collections.immutable.toPersistentList

                @CollectionWrapper(forType = PersistentList::class)
                object PersistentListWrapper {
                    $wrapperBody
                }

                @KMapperConfig(wrappers = [PersistentListWrapper::class])
                object MappingConfig

                data class DomainModel(val tags: PersistentList<String>)

                @MapTo(DomainModel::class)
                data class DataModel(val tags: List<String>)
            """.trimIndent(),
        )

        val validWrap = "fun <T> wrap(source: List<T>): PersistentList<T> = source.toPersistentList()"
        val invalidWrap = "fun <T> wrap(source: Set<T>): PersistentList<T> = source.toList().toPersistentList()"

        given("a wrapper declaring a VALID wrap plus an INVALID same-name overload (valid first)") {
            `when`("the mapping is compiled") {
                val (compilationResult, compilation) =
                    compile(
                        wrapperSource(
                            """
                            $validWrap
                            $invalidWrap
                            """.trimIndent(),
                        ),
                    )

                then("compilation fails — the invalid overload is reported, never silently skipped") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                    compilationResult.messages shouldContain wrongShapeMarker
                }

                then("the valid overload still provides the wrap direction — no cascading missing-direction error") {
                    compilationResult.messages shouldNotContain noWrapDirectionMarker
                    compilation.generatedFile("DataModelMappers.kt") shouldContain "PersistentListWrapper.wrap("
                }
            }
        }

        given("a wrapper declaring an INVALID wrap overload before the VALID one (invalid first)") {
            `when`("the mapping is compiled") {
                val (compilationResult, compilation) =
                    compile(
                        wrapperSource(
                            """
                            $invalidWrap
                            $validWrap
                            """.trimIndent(),
                        ),
                    )

                then("compilation fails with the same guided overload error") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                    compilationResult.messages shouldContain wrongShapeMarker
                }

                then("the valid overload still provides the wrap direction (order-independent)") {
                    compilationResult.messages shouldNotContain noWrapDirectionMarker
                    compilation.generatedFile("DataModelMappers.kt") shouldContain "PersistentListWrapper.wrap("
                }
            }
        }

        given("a wrapper whose ONLY wrap candidate has the wrong shape") {
            `when`("the mapping is compiled") {
                val (compilationResult, _) = compile(wrapperSource(invalidWrap))

                then("compilation fails quoting the expected signature") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                    compilationResult.messages shouldContain wrongShapeMarker
                    compilationResult.messages shouldContain
                        "fun <T> wrap(source: List<T>): PersistentList<T>"
                }
            }
        }

        given("a wrap whose containers match but whose type arguments are NOT the function's own type parameter") {
            `when`("fun <T> wrap(source: List<String>): PersistentList<Int> is compiled") {
                val (compilationResult, _) =
                    compile(
                        wrapperSource(
                            "fun <T> wrap(source: List<String>): PersistentList<Int> = persistentListOf()",
                        ),
                    )

                then("compilation fails — type-argument linkage is enforced, not silently accepted") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                }

                then("the error quotes the expected generic signature") {
                    compilationResult.messages shouldContain wrongShapeMarker
                    compilationResult.messages shouldContain
                        "fun <T> wrap(source: List<T>): PersistentList<T>"
                }
            }
        }
    })
