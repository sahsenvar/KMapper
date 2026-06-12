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
 * Runtime-execution tests for field-anchored @Validate at the Result boundary.
 *
 * Each test compiles source strings that embed tiny inline Validator object definitions
 * (so :processor tests have no extra module dependency), classloads the result and invokes
 * the generated mapper via [invokeResultMapper]. Validation failures are hard: they surface
 * as `Result.failure` carrying [MappingException.ValidationFailed].
 */
class ValidateRuntimeTest :
    BehaviorSpec({

        given("@Validate on a non-null SOURCE field") {
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "VFR1.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        import com.sahsenvar.kmapper.annotations.Validate
                        import com.sahsenvar.kmapper.validation.Validator

                        data class TitleDomainModel(val title: String)

                        object NotBlankValidator : Validator<String>(String::class) {
                            override fun validate(value: String): String? =
                                if (value.isBlank()) "must not be blank" else null
                        }

                        @MapTo(TitleDomainModel::class)
                        data class TitleDataModel(
                            @Validate(NotBlankValidator::class) val title: String
                        )
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the source value is blank") {
                then("the mapping fails with ValidationFailed") {
                    val outcome =
                        result.invokeResultMapper(
                            "TitleDataModelMappersKt",
                            "toTitleDomainModelResult",
                            result.newInstance("TitleDataModel", "   "),
                        )
                    outcome.isFailure.shouldBeTrue()
                    outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.ValidationFailed>()
                }
            }

            `when`("the source value is valid") {
                then("the mapping succeeds") {
                    val outcome =
                        result.invokeResultMapper(
                            "TitleDataModelMappersKt",
                            "toTitleDomainModelResult",
                            result.newInstance("TitleDataModel", "hello"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("title") shouldBe "hello"
                }
            }
        }

        given("@Validate on a non-null TARGET field") {
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "VTR1.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        import com.sahsenvar.kmapper.annotations.Validate
                        import com.sahsenvar.kmapper.validation.Validator

                        object NotBlankValidator : Validator<String>(String::class) {
                            override fun validate(value: String): String? =
                                if (value.isBlank()) "must not be blank" else null
                        }

                        data class CodeDomainModel(
                            @Validate(NotBlankValidator::class) val code: String
                        )

                        @MapTo(CodeDomainModel::class)
                        data class CodeDataModel(val code: String)
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the produced result value is invalid") {
                then("the mapping fails with ValidationFailed") {
                    val outcome =
                        result.invokeResultMapper(
                            "CodeDataModelMappersKt",
                            "toCodeDomainModelResult",
                            result.newInstance("CodeDataModel", "  "),
                        )
                    outcome.isFailure.shouldBeTrue()
                    outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.ValidationFailed>()
                }
            }

            `when`("the produced result value is valid") {
                then("the mapping succeeds") {
                    val outcome =
                        result.invokeResultMapper(
                            "CodeDataModelMappersKt",
                            "toCodeDomainModelResult",
                            result.newInstance("CodeDataModel", "VALID"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("code") shouldBe "VALID"
                }
            }
        }

        given("@Validate on a nullable SOURCE field whose target declares a default") {
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "VFR2.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        import com.sahsenvar.kmapper.annotations.Validate
                        import com.sahsenvar.kmapper.validation.Validator

                        data class LabelDomainModel(val label: String = "default")

                        object NotBlankValidator : Validator<String>(String::class) {
                            override fun validate(value: String): String? =
                                if (value.isBlank()) "must not be blank" else null
                        }

                        @MapTo(LabelDomainModel::class)
                        data class LabelDataModel(
                            @Validate(NotBlankValidator::class)
                            val label: String?
                        )
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the source value is null") {
                then("validation is SKIPPED and the constructor default applies") {
                    val outcome =
                        result.invokeResultMapper(
                            "LabelDataModelMappersKt",
                            "toLabelDomainModelResult",
                            result.newInstance("LabelDataModel", null as String?),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("label") shouldBe "default"
                }
            }

            `when`("the source value is valid") {
                then("the value passes through") {
                    val outcome =
                        result.invokeResultMapper(
                            "LabelDataModelMappersKt",
                            "toLabelDomainModelResult",
                            result.newInstance("LabelDataModel", "hello"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("label") shouldBe "hello"
                }
            }

            `when`("the source value is present but blank") {
                then("the mapping fails with ValidationFailed") {
                    val outcome =
                        result.invokeResultMapper(
                            "LabelDataModelMappersKt",
                            "toLabelDomainModelResult",
                            result.newInstance("LabelDataModel", "  "),
                        )
                    outcome.isFailure.shouldBeTrue()
                    outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.ValidationFailed>()
                }
            }
        }

        given("a mapping with no validation annotations") {
            val (result, _) =
                compile(
                    SourceFile.kotlin(
                        "NoValR.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        data class SimpleDomainModel(val x: String)
                        @MapTo(SimpleDomainModel::class)
                        data class SimpleDataModel(val x: String)
                        """.trimIndent(),
                    ),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            `when`("the mapper runs") {
                then("behaviour is unchanged — plain success") {
                    val outcome =
                        result.invokeResultMapper(
                            "SimpleDataModelMappersKt",
                            "toSimpleDomainModelResult",
                            result.newInstance("SimpleDataModel", "hello"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("x") shouldBe "hello"
                }
            }
        }
    })
