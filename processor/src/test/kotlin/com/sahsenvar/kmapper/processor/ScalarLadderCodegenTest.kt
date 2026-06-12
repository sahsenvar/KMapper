@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.KMapper
import com.sahsenvar.kmapper.MappingDegradation
import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.MappingListener
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Records every [MappingDegradation] dispatched through the shared [KMapper] listener registry.
 * The kctfork classloader resolves core types parent-first, so generated code and the test
 * observe the SAME KMapper object — registration here taps generated-code dispatches.
 */
private class RecordingDegradationListener : MappingListener {
    val events = mutableListOf<MappingDegradation>()

    override fun onDegradation(event: MappingDegradation) {
        events.add(event)
    }
}

/** Runs [block] with a registered recording listener, always unregistering afterwards. */
private inline fun <T> withRecordingListener(block: (RecordingDegradationListener) -> T): T {
    val listener = RecordingDegradationListener()
    KMapper.addListener(listener)
    return try {
        block(listener)
    } finally {
        KMapper.removeListener(listener)
    }
}

/**
 * Golden tests for the scalar fallback-ladder codegen (plan Task 14): `Result` boundary,
 * seam selection per (landing shape × onFail policy), omit/copy defaults, validation
 * emission, and the runtime behavior of the generated seams (report rule included).
 */
class ScalarLadderCodegenTest :
    BehaviorSpec({

        given("a DataModel with hard, nullable, and defaulted targets") {
            val source =
                SourceFile.kotlin(
                    "Models.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class UserDomainModel(val id: Long, val age: Int?, val plan: String = "FREE")

                    @MapTo(UserDomainModel::class)
                    data class UserDataModel(val id: String, val age: String?, val plan: String?)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "UserDataModelMappers.kt")

                then("the function returns Result and is named toXResult") {
                    generated shouldContain "fun UserDataModel.toUserDomainModelResult(): Result<UserDomainModel>"
                    generated shouldContain "runCatching"
                }

                then("the hard cell uses convertOrFail with path/type literals and the total method") {
                    generated shouldContain "convertOrFail(\"id\", \"kotlin.String\", \"kotlin.Long\")"
                    generated shouldContain "LongStringConverter.convertFrom(it)"
                }

                then("the nullable target uses convertOrNull with the OrNull converter method") {
                    generated shouldContain "convertOrNull(\"age\", \"kotlin.String\", \"kotlin.Int\")"
                    generated shouldContain "IntStringConverter.convertFromOrNull(it)"
                }

                then("the defaulted target is omitted from the constructor and set via copy") {
                    generated shouldContain "val base = UserDomainModel("
                    generated shouldContain "base.copy("
                    // Direct String? -> String in the copy stage falls back to the base default.
                    generated shouldContain "plan ?: base.plan"
                }

                then("both listener dispatches stay inside runCatching") {
                    generated shouldContain "onMapStart(this@toUserDomainModelResult"
                    generated shouldContain "onMapComplete(this@toUserDomainModelResult, result)"
                }
            }
        }

        given("ladder row 2: non-null source into a converter-backed defaulted target") {
            val source =
                SourceFile.kotlin(
                    "Row2.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class RetryDomainModel(val retries: Int = 3)

                    @MapTo(RetryDomainModel::class)
                    data class RetryDataModel(val retries: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "RetryDataModelMappers.kt")

                then("the copy stage rides convertOrElse with the base fallback and OrNull method") {
                    generated shouldContain "convertOrElse(\"retries\", \"kotlin.String\", \"kotlin.Int\", base.retries)"
                    generated shouldContain "IntStringConverter.convertFromOrNull(it)"
                }
            }

            `when`("the mapping runs with a broken source value") {
                val (result, _) = compile(source)

                then("the default applies and exactly one AbsorbedConversionError is reported") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "RetryDataModelMappersKt",
                                "toRetryDomainModelResult",
                                result.newInstance("RetryDataModel", "not-a-number"),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("retries") shouldBe 3
                        listener.events.shouldHaveSize(1)
                        val event = listener.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                        event.path shouldBe "retries"
                        event.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                    }
                }
            }
        }

        given("ladder row 6: nullable source into a defaulted target (absent silent, broken reported)") {
            val source =
                SourceFile.kotlin(
                    "Row6.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class PlanDomainModel(val level: Int = 7)

                    @MapTo(PlanDomainModel::class)
                    data class PlanDataModel(val level: String?)
                    """.trimIndent(),
                )
            val (result, _) = compile(source)

            `when`("the source value is absent (null)") {
                then("the default applies SILENTLY — no degradation event") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "PlanDataModelMappersKt",
                                "toPlanDomainModelResult",
                                result.newInstance("PlanDataModel", null as String?),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("level") shouldBe 7
                        listener.events.shouldBeEmpty()
                    }
                }
            }

            `when`("the source value is broken") {
                then("the default applies AND an AbsorbedConversionError is reported") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "PlanDataModelMappersKt",
                                "toPlanDomainModelResult",
                                result.newInstance("PlanDataModel", "broken"),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("level") shouldBe 7
                        listener.events.shouldHaveSize(1)
                        listener.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>().path shouldBe "level"
                    }
                }
            }
        }

        given("ladder row 5: nullable source, non-null target, no default (hard cell)") {
            val source =
                SourceFile.kotlin(
                    "Row5.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class IdDomainModel(val id: Long)

                    @MapTo(IdDomainModel::class)
                    data class IdDataModel(val id: String?)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the hard seam is emitted (nullable receiver overload guards absence)") {
                    val generated = compilation.generatedFile("IdDataModelMappers.kt")
                    generated shouldContain "id.convertOrFail(\"id\", \"kotlin.String\", \"kotlin.Long\")"
                }
            }

            `when`("the source value is absent (null)") {
                then("the mapping fails hard with RequiredFieldMissing at path 'id'") {
                    val outcome =
                        result.invokeResultMapper(
                            "IdDataModelMappersKt",
                            "toIdDomainModelResult",
                            result.newInstance("IdDataModel", null as String?),
                        )
                    outcome.isFailure.shouldBeTrue()
                    val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.RequiredFieldMissing>()
                    exception.path shouldBe "id"
                }
            }

            `when`("the source value is broken") {
                then("the mapping fails hard with TypeConversionFailed at path 'id'") {
                    val outcome =
                        result.invokeResultMapper(
                            "IdDataModelMappersKt",
                            "toIdDomainModelResult",
                            result.newInstance("IdDataModel", "abc"),
                        )
                    outcome.isFailure.shouldBeTrue()
                    val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                    exception.path shouldBe "id"
                }
            }

            `when`("the source value converts cleanly") {
                then("the mapping succeeds with the converted value") {
                    val outcome =
                        result.invokeResultMapper(
                            "IdDataModelMappersKt",
                            "toIdDomainModelResult",
                            result.newInstance("IdDataModel", "42"),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("id") shouldBe 42L
                }
            }
        }

        given("OnFail.Throw on a nullable target") {
            val source =
                SourceFile.kotlin(
                    "Strict.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.OnFail

                    data class StrictDomainModel(val age: Int?)

                    @MapTo(StrictDomainModel::class)
                    data class StrictDataModel(@ConvertWith(onFail = OnFail.Throw) val age: String?)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the strict seam is emitted") {
                    val generated = compilation.generatedFile("StrictDataModelMappers.kt")
                    generated shouldContain "convertOrNullStrict(\"age\", \"kotlin.String\", \"kotlin.Int\")"
                }
            }

            `when`("the source value is broken") {
                then("the mapping fails hard despite the nullable escape") {
                    val outcome =
                        result.invokeResultMapper(
                            "StrictDataModelMappersKt",
                            "toStrictDomainModelResult",
                            result.newInstance("StrictDataModel", "abc"),
                        )
                    outcome.isFailure.shouldBeTrue()
                    outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.TypeConversionFailed>().path shouldBe "age"
                }
            }

            `when`("the source value is absent (null)") {
                then("absence stays type-driven — null lands silently even under Throw") {
                    val outcome =
                        result.invokeResultMapper(
                            "StrictDataModelMappersKt",
                            "toStrictDomainModelResult",
                            result.newInstance("StrictDataModel", null as String?),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("age").shouldBeNull()
                }
            }
        }

        given("@IgnoreDefaultValue on a defaulted target field") {
            val source =
                SourceFile.kotlin(
                    "NoFallback.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.IgnoreDefaultValue
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class QuotaDomainModel(@IgnoreDefaultValue val quota: Long = 1L)

                    @MapTo(QuotaDomainModel::class)
                    data class QuotaDataModel(val quota: String?)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "QuotaDataModelMappers.kt")

                then("the field is built in the constructor stage with the hard seam (no copy)") {
                    generated shouldContain "convertOrFail(\"quota\", \"kotlin.String\", \"kotlin.Long\")"
                    generated shouldNotContain "base.copy("
                }
            }
        }

        given("@Validate on source and target fields") {
            val source =
                SourceFile.kotlin(
                    "Validated.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.Validate
                    import com.sahsenvar.kmapper.validation.Validator

                    object RawFormat : Validator<String>(String::class) {
                        override fun validate(value: String): String? =
                            if (value.startsWith("v")) null else "must start with v"
                    }

                    object Positive : Validator<Int>(Int::class) {
                        override fun validate(value: Int): String? =
                            if (value > 0) null else "must be positive"
                    }

                    data class ScoreDomainModel(@Validate(Positive::class) val score: Int)

                    @MapTo(ScoreDomainModel::class)
                    data class ScoreDataModel(@Validate(RawFormat::class) val score: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "ScoreDataModelMappers.kt")

                then("the source validator fires before the mapping, the target validator after") {
                    generated shouldContain "RawFormat.validate"
                    generated shouldContain "Positive.validate"
                    generated.indexOf("RawFormat.validate") shouldBeLessThan generated.indexOf("Positive.validate")
                }
            }
        }

        given("a @ConvertFrom(onFail = Throw) directive on the reverse-source field") {
            val source =
                SourceFile.kotlin(
                    "Asym.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertFrom
                    import com.sahsenvar.kmapper.annotations.MapFrom
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.OnFail

                    data class ScoreDomainModel(@ConvertFrom(onFail = OnFail.Throw) val score: Int?)

                    @MapTo(ScoreDomainModel::class)
                    @MapFrom(ScoreDomainModel::class)
                    data class ScoreDataModel(val score: String?)
                    """.trimIndent(),
                )
            val (_, compilation) = compile(source)

            `when`("the forward (@MapTo) function is generated") {
                then("the Auto seam is emitted — the directive scopes to the reverse direction only") {
                    val generated = compilation.generatedFile("ScoreDataModelMappers.kt")
                    generated shouldContain "convertOrNull(\"score\", \"kotlin.String\", \"kotlin.Int\")"
                    generated shouldNotContain "convertOrNullStrict"
                }
            }

            `when`("the reverse (@MapFrom) function is generated") {
                then("the strict seam is emitted with the opposite converter orientation") {
                    val generated = compilation.generatedFile("ScoreDomainModelMappers.kt")
                    generated shouldContain "convertOrNullStrict(\"score\", \"kotlin.Int\", \"kotlin.String\")"
                    generated shouldContain "IntStringConverter.convertToOrNull(it)"
                }
            }
        }

        given("OnFail.Skip on a scalar field") {
            val source =
                SourceFile.kotlin(
                    "BadSkip.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.OnFail

                    data class SkipDomainModel(val age: Int?)

                    @MapTo(SkipDomainModel::class)
                    data class SkipDataModel(@ConvertWith(onFail = OnFail.Skip) val age: String?)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("compilation fails with the precondition message") {
                    errMessages(source) shouldContain "OnFail.Skip applies to collection elements only"
                }
            }
        }
    })
