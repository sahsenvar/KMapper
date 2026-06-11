@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Element-level converter RESOLUTION parity across all three container shapes — plain
 * Collection, Map values, and @CollectionWrapper targets — which now share one
 * `resolveElementStrategy` path in TypeMatcher.
 *
 * The historical bug: the wrapper branch fell back to Direct for any non-same-type,
 * non-data-class element pair, silently skipping converter resolution. With the shared
 * path, an element pair with an intentionally-unsupported direction surfaces the
 * @UnsupportedDirection reason, and a pair with no converter at all surfaces the
 * MissingConverter guidance — exactly like the other two container shapes.
 *
 * Assertions are diagnostic-level (KSP error messages) on purpose: element-level Convert
 * CODEGEN is the follow-up codegen chunk (see the INTERIM note in MappingCodeGenerator),
 * so resolution is observable through compile-time diagnostics, not generated calls.
 */
class WrappedCollectionElementResolutionTest :
    BehaviorSpec({

        // Shared @CollectionWrapper fixture: kotlinx-collections-immutable is on the test classpath.
        fun wrappedSource(
            targetElement: String,
            sourceElement: String,
            extraDeclarations: String = "",
        ) = SourceFile.kotlin(
            "WrappedModels.kt",
            """
            import com.sahsenvar.kmapper.annotations.CollectionWrapper
            import com.sahsenvar.kmapper.annotations.KMapperConfig
            import com.sahsenvar.kmapper.annotations.MapTo
            import kotlinx.collections.immutable.PersistentList
            import kotlinx.collections.immutable.toPersistentList

            @CollectionWrapper(forType = PersistentList::class)
            object PersistentListWrapper {
                fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
            }

            @KMapperConfig(wrappers = [PersistentListWrapper::class])
            object MappingConfig

            $extraDeclarations

            data class DomainModel(val values: PersistentList<$targetElement>)

            @MapTo(DomainModel::class)
            data class DataModel(val values: List<$sourceElement>)
            """.trimIndent(),
        )

        given("a wrapped PersistentList target whose element pair direction is intentionally unsupported") {
            // Long -> Int is the lossy, @UnsupportedDirection-annotated direction of LongIntConverter.
            `when`("List<Long> -> PersistentList<Int> is compiled") {
                val (compilationResult, _) = compile(wrappedSource(targetElement = "Int", sourceElement = "Long"))

                then("compilation fails — the wrapper branch resolves elements, no silent Direct") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                }

                then("the error carries the element pair's @UnsupportedDirection reason") {
                    compilationResult.messages shouldContain "narrows and can truncate"
                }
            }
        }

        given("a wrapped PersistentList target whose element pair has no converter at all") {
            `when`("List<String> -> PersistentList<Opaque> is compiled (Opaque is not a data class)") {
                val (compilationResult, _) =
                    compile(
                        wrappedSource(
                            targetElement = "Opaque",
                            sourceElement = "String",
                            extraDeclarations = "class Opaque(val raw: String)",
                        ),
                    )

                then("compilation fails") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                }

                then("the error carries the MissingConverter guidance") {
                    compilationResult.messages shouldContain "has no registered converter"
                }
            }
        }

        given("the same unsupported element pair in the other two container shapes (parity check)") {
            `when`("plain List<Long> -> List<Int> is compiled") {
                val plainListSource =
                    SourceFile.kotlin(
                        "PlainListModels.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo

                        data class DomainModel(val values: List<Int>)

                        @MapTo(DomainModel::class)
                        data class DataModel(val values: List<Long>)
                        """.trimIndent(),
                    )
                val (compilationResult, _) = compile(plainListSource)

                then("the identical @UnsupportedDirection reason surfaces") {
                    compilationResult.messages shouldContain "narrows and can truncate"
                }
            }

            `when`("Map<String, Long> -> Map<String, Int> is compiled") {
                val mapValuesSource =
                    SourceFile.kotlin(
                        "MapValueModels.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo

                        data class DomainModel(val values: Map<String, Int>)

                        @MapTo(DomainModel::class)
                        data class DataModel(val values: Map<String, Long>)
                        """.trimIndent(),
                    )
                val (compilationResult, _) = compile(mapValuesSource)

                then("the identical @UnsupportedDirection reason surfaces") {
                    compilationResult.messages shouldContain "narrows and can truncate"
                }
            }
        }
    })
