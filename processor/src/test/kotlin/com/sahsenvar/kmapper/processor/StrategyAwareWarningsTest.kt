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
 * Compile-time WARNING diagnostics, asserted through kctfork's captured `result.messages`.
 *
 * 1. The dead-'?' warning ("target is nullable but the mapping never produces null") is
 *    STRATEGY-AWARE: it fires only when the resolved strategy provably never yields null —
 *    never for OptionUnwrap (None becomes null) or for a converter direction that declares
 *    an OrNull variant (sanctioned null: the converter may legitimately return null for
 *    inputs with no counterpart, e.g. a blank string carrying no Int).
 * 2. @IgnoreDefaultValue on a field with no constructor default is a no-op — flagged with
 *    a warning so the author learns the annotation does nothing there.
 */
class StrategyAwareWarningsTest :
    BehaviorSpec({

        val deadNullableMarker = "dead '?'"

        // Simulates arrow.core.Option by FQN — processor detection is FQN-string based.
        val arrowStubSource =
            SourceFile.kotlin(
                "ArrowStub.kt",
                """
                package arrow.core

                class Option<out A> private constructor(val value: A?) {
                    fun getOrNull(): A? = value
                    companion object {
                        fun <A> fromNullable(a: A?): Option<A> = Option(a)
                    }
                }
                """.trimIndent(),
            )

        given("an Option<String> source unwrapped into a nullable String target") {
            val optionUnwrapSource =
                SourceFile.kotlin(
                    "OptionUnwrapModels.kt",
                    """
                    import arrow.core.Option
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class DomainModel(val maybeName: String?)

                    @MapTo(DomainModel::class)
                    data class DataModel(val maybeName: Option<String>)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, _) = compile(arrowStubSource, optionUnwrapSource)

                then("compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("NO dead-'?' warning fires — OptionUnwrap produces null for None") {
                    compilationResult.messages shouldNotContain deadNullableMarker
                }
            }
        }

        given("a non-null Int mapped into a nullable String target via a built-in with no OrNull variant") {
            val builtInConvertSource =
                SourceFile.kotlin(
                    "BuiltInConvertModels.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class DomainModel(val count: String?)

                    @MapTo(DomainModel::class)
                    data class DataModel(val count: Int)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, _) = compile(builtInConvertSource)

                then("compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("the dead-'?' warning fires — the total conversion can never yield null") {
                    compilationResult.messages shouldContain deadNullableMarker
                }
            }
        }

        given("a non-null Int mapped into a nullable String target via a converter WITH an OrNull override") {
            val orNullConverterSource =
                SourceFile.kotlin(
                    "OrNullConverterModels.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.KMapperConfig
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    /** Sanctioned-null style: zero has no String counterpart in this domain. */
                    object NullableCountConverter : MapTypeConverter<Int, String>(Int::class, String::class) {
                        override fun convertTo(source: Int): String = source.toString()

                        override fun convertToOrNull(source: Int): String? = if (source == 0) null else source.toString()
                    }

                    @KMapperConfig(converters = [NullableCountConverter::class])
                    object MappingConfig

                    data class DomainModel(val count: String?)

                    @MapTo(DomainModel::class)
                    data class DataModel(val count: Int)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, _) = compile(orNullConverterSource)

                then("compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("NO dead-'?' warning fires — the declared OrNull variant sanctions null") {
                    compilationResult.messages shouldNotContain deadNullableMarker
                }
            }
        }

        given("a non-null wire source mapped into a NULLABLE enum target (EnumFromWire)") {
            val nullableEnumTargetSource =
                SourceFile.kotlin(
                    "NullableEnumTargetModels.kt",
                    """
                    import com.sahsenvar.kmapper.MappableEnum
                    import com.sahsenvar.kmapper.annotations.MapTo

                    enum class Status(override val wireValue: String) : MappableEnum<String> {
                        PENDING("PENDING"), SHIPPED("in_transit");
                    }

                    data class DomainModel(val status: Status?)

                    @MapTo(DomainModel::class)
                    data class DataModel(val status: String)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, _) = compile(nullableEnumTargetSource)

                then("compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("NO dead-'?' warning fires — an unknown wire value absorbs to null at the nullable landing") {
                    compilationResult.messages shouldNotContain deadNullableMarker
                }
            }
        }

        given("@IgnoreDefaultValue on a field that declares no constructor default") {
            val defaultlessIgnoreSource =
                SourceFile.kotlin(
                    "DefaultlessIgnoreModels.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.IgnoreDefaultValue
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class DomainModel(val name: String)

                    @MapTo(DomainModel::class)
                    data class DataModel(
                        @IgnoreDefaultValue val name: String,
                    )
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, _) = compile(defaultlessIgnoreSource)

                then("compilation succeeds — the annotation is a no-op, not an error") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("a warning explains the annotation has no effect without a default") {
                    compilationResult.messages shouldContain "@IgnoreDefaultValue has no effect"
                }
            }
        }

        given("@IgnoreDefaultValue on a field WITH a constructor default") {
            val defaultedIgnoreSource =
                SourceFile.kotlin(
                    "DefaultedIgnoreModels.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.IgnoreDefaultValue
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class DomainModel(val name: String)

                    @MapTo(DomainModel::class)
                    data class DataModel(
                        @IgnoreDefaultValue val name: String = "unset",
                    )
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, _) = compile(defaultedIgnoreSource)

                then("compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("no no-op warning fires — the annotation has a real default to suppress") {
                    compilationResult.messages shouldNotContain "@IgnoreDefaultValue has no effect"
                }
            }
        }
    })
