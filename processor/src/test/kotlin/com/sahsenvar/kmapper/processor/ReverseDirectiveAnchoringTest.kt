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
 * Issue #82: in the @MapFrom (reverse) direction, per-field converter directives are read from
 * the TARGET field (the class declaring the mapping) first, then from the source field.
 * The source of a @MapFrom is typically a foreign class the user cannot annotate.
 */
class ReverseDirectiveAnchoringTest :
    BehaviorSpec({
        val textConverters =
            """
            object PriceTextConverter : MapTypeConverter<Double, String>(Double::class, String::class) {
                override fun convertTo(source: Double): String = "price:" + source
            }

            object PercentTextConverter : MapTypeConverter<Double, String>(Double::class, String::class) {
                override fun convertTo(source: Double): String = "percent:" + source
            }
            """.trimIndent()

        given("@ConvertFrom(use) and @ConvertWith(use) on @MapFrom target fields, a foreign source") {
            val source =
                SourceFile.kotlin(
                    "TargetUse.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertFrom
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapFrom
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    $textConverters

                    data class StatsDomainModel(val mark: Double, val iv: Double)

                    @MapFrom(StatsDomainModel::class)
                    data class StatsDataModel(
                        @ConvertFrom(use = PriceTextConverter::class) val mark: String,
                        @ConvertWith(use = PercentTextConverter::class) val iv: String,
                    )
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the build succeeds") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("each field calls the converter declared on the target field") {
                    val generated = compilation.generatedFile("StatsDomainModelMappers.kt")
                    generated shouldContain "PriceTextConverter"
                    generated shouldContain "PercentTextConverter"
                }
            }

            `when`("the generated mapper runs") {
                then("each field carries its own per-field format") {
                    val mapped =
                        result.invokeMapper(
                            "StatsDomainModelMappersKt",
                            "toStatsDataModel",
                            result.newInstance("StatsDomainModel", 1.5, 0.25),
                        )!!
                    mapped.prop("mark") shouldBe "price:1.5"
                    mapped.prop("iv") shouldBe "percent:0.25"
                }
            }
        }

        given("@ConvertFrom(onFail = Throw) on a @MapFrom target field") {
            val source =
                SourceFile.kotlin(
                    "TargetOnFail.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertFrom
                    import com.sahsenvar.kmapper.annotations.MapFrom
                    import com.sahsenvar.kmapper.annotations.OnFail

                    data class ScoreDataModel(val score: String?)

                    @MapFrom(ScoreDataModel::class)
                    data class ScoreDomainModel(@ConvertFrom(onFail = OnFail.Throw) val score: Int?)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the reverse function is generated") {
                then("the strict seam is emitted — the policy reaches codegen, not only resolution") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    val generated = compilation.generatedFile("ScoreDataModelMappers.kt")
                    generated shouldContain "convertOrNullStrict(\"score\""
                }
            }
        }

        given("a single class declaring both directions with direction-scoped directives") {
            val source =
                SourceFile.kotlin(
                    "BothDirections.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertFrom
                    import com.sahsenvar.kmapper.annotations.ConvertTo
                    import com.sahsenvar.kmapper.annotations.MapFrom
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.OnFail

                    data class ScoreDomainModel(val score: Int?)

                    @MapTo(ScoreDomainModel::class)
                    @MapFrom(ScoreDomainModel::class)
                    data class ScoreDataModel(
                        @ConvertTo(onFail = OnFail.Throw) @ConvertFrom(onFail = OnFail.Auto) val score: String?,
                    )
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the forward (@MapTo) function is generated") {
                then("the outgoing @ConvertTo(onFail = Throw) emits the strict seam") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    val generated = compilation.generatedFile("ScoreDataModelMappers.kt")
                    generated shouldContain "convertOrNullStrict(\"score\", \"kotlin.String\", \"kotlin.Int\")"
                }
            }

            `when`("the reverse (@MapFrom) function is generated") {
                then("the @ConvertFrom on the same class governs — the Auto seam is emitted") {
                    val generated = compilation.generatedFile("ScoreDomainModelMappers.kt")
                    generated shouldContain "convertOrNull(\"score\", \"kotlin.Int\", \"kotlin.String\")"
                    generated shouldNotContain "convertOrNullStrict"
                }
            }
        }

        given("directives on both the @MapFrom target field and the source field") {
            val source =
                SourceFile.kotlin(
                    "BothSides.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertFrom
                    import com.sahsenvar.kmapper.annotations.MapFrom
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    $textConverters

                    data class StatsDomainModel(@ConvertFrom(use = PercentTextConverter::class) val mark: Double)

                    @MapFrom(StatsDomainModel::class)
                    data class StatsDataModel(@ConvertFrom(use = PriceTextConverter::class) val mark: String)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the target's directive wins") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    val generated = compilation.generatedFile("StatsDomainModelMappers.kt")
                    generated shouldContain "PriceTextConverter"
                    generated shouldNotContain "PercentTextConverter"
                }

                then("a warning names the ignored source directive") {
                    result.messages shouldContain "the target's directive wins and the source's is ignored"
                }
            }
        }

        given("@ConvertFrom(onFail = Skip) on a scalar @MapFrom target field") {
            val source =
                SourceFile.kotlin(
                    "TargetSkip.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertFrom
                    import com.sahsenvar.kmapper.annotations.MapFrom
                    import com.sahsenvar.kmapper.annotations.OnFail

                    data class AmountDomainModel(val amount: Int)

                    @MapFrom(AmountDomainModel::class)
                    data class AmountDataModel(@ConvertFrom(onFail = OnFail.Skip) val amount: Long)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the Skip precondition fires instead of the directive being silently dropped") {
                    errMessages(source) shouldContain "OnFail.Skip applies to collection elements only"
                }
            }
        }

        given("a @ConvertFrom directive only on the @MapFrom source field (pre-#82 placement)") {
            val source =
                SourceFile.kotlin(
                    "SourceFallback.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertFrom
                    import com.sahsenvar.kmapper.annotations.MapFrom
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    $textConverters

                    data class StatsDomainModel(@ConvertFrom(use = PriceTextConverter::class) val mark: Double)

                    @MapFrom(StatsDomainModel::class)
                    data class StatsDataModel(val mark: String)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the source directive still applies as a fallback, without a warning") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    compilation.generatedFile("StatsDomainModelMappers.kt") shouldContain "PriceTextConverter"
                    result.messages shouldNotContain "the target's directive wins"
                }
            }
        }
    })
