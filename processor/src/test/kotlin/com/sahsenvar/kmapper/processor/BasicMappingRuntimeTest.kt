@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.MappingException
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Runtime-execution tests for the KSP mapping processor at the Result boundary.
 *
 * Compiles source with the processor attached, classloads the generated mappers, and
 * invokes them reflectively via [invokeResultMapper]. The library never throws at the
 * caller: hard failures surface as `Result.failure` carrying the typed [MappingException].
 *
 * A FAILURE here is a real production bug — do NOT weaken these assertions.
 */
class BasicMappingRuntimeTest :
    BehaviorSpec({

        given("a same-shape field-by-field mapping") {
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "M.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        data class UserDomainModel(val id: String, val email: String)
                        @MapTo(UserDomainModel::class)
                        data class UserDataModel(val id: String, val email: String)
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the mapper runs") {
                then("all fields copy through and the boundary reports success") {
                    val outcome =
                        result.invokeResultMapper(
                            "UserDataModelMappersKt",
                            "toUserDomainModelResult",
                            result.newInstance("UserDataModel", "42", "a@b.com"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    val domain = outcome.getOrNull()!!
                    domain.prop("id") shouldBe "42"
                    domain.prop("email") shouldBe "a@b.com"
                }
            }
        }

        given("a nullable source field mapped into a non-null target without a default") {
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "N.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        data class StrictDomainModel(val id: String)
                        @MapTo(StrictDomainModel::class)
                        data class StrictDataModel(val id: String?)
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the source value is null") {
                then("the boundary reports failure with RequiredFieldMissing") {
                    val outcome =
                        result.invokeResultMapper(
                            "StrictDataModelMappersKt",
                            "toStrictDomainModelResult",
                            result.newInstance("StrictDataModel", null as String?),
                        )
                    outcome.isFailure.shouldBeTrue()
                    outcome
                        .exceptionOrNull()
                        .shouldBeInstanceOf<MappingException.RequiredFieldMissing>()
                        .path shouldBe "id"
                }
            }
        }

        given("a built-in String to Int conversion") {
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "C.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        data class CountDomainModel(val n: Int)
                        @MapTo(CountDomainModel::class)
                        data class CountDataModel(val n: String)
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the source parses cleanly") {
                then("the converted integer lands in the domain model") {
                    val outcome =
                        result.invokeResultMapper(
                            "CountDataModelMappersKt",
                            "toCountDomainModelResult",
                            result.newInstance("CountDataModel", "7"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("n") shouldBe 7
                }
            }

            `when`("the source is malformed") {
                then("the raw converter exception is wrapped as TypeConversionFailed — never escapes raw") {
                    val outcome =
                        result.invokeResultMapper(
                            "CountDataModelMappersKt",
                            "toCountDomainModelResult",
                            result.newInstance("CountDataModel", "abc"),
                        )
                    outcome.isFailure.shouldBeTrue()
                    // If this surfaces a raw NumberFormatException instead, the seam wrapping
                    // has a hole — a REAL production bug.
                    outcome
                        .exceptionOrNull()
                        .shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                        .path shouldBe "n"
                }
            }
        }

        given("a target field with a constructor default and a nullable source") {
            // Old-world intent (@MapDefaultValue substitutes when source is null) carries over:
            // the default now lives in the TARGET constructor and applies via omit/copy.
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "D.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        data class ProfileDomainModel(val name: String, val score: Int = 0)
                        @MapTo(ProfileDomainModel::class)
                        data class ProfileDataModel(
                            val name: String,
                            val score: String?
                        )
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the source value is absent (null)") {
                then("the constructor default substitutes silently") {
                    val outcome =
                        result.invokeResultMapper(
                            "ProfileDataModelMappersKt",
                            "toProfileDomainModelResult",
                            result.newInstance("ProfileDataModel", "Alice", null as String?),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    val domain = outcome.getOrNull()!!
                    domain.prop("name") shouldBe "Alice"
                    domain.prop("score") shouldBe 0
                }
            }

            `when`("the source value is present") {
                then("the converted value overrides the default") {
                    val outcome =
                        result.invokeResultMapper(
                            "ProfileDataModelMappersKt",
                            "toProfileDomainModelResult",
                            result.newInstance("ProfileDataModel", "Alice", "55"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("score") shouldBe 55
                }
            }
        }
    })
