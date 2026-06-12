@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Converter configuration in the redesigned world: @KMapperConfig registers global converters
 * by type pair (no per-field annotation needed), @ConvertWith(use = …) overrides per field —
 * including a SAME-pair converter alongside the global one — duplicate pairs inside
 * @KMapperConfig stay a compile error, and a pair with no converter at all produces the
 * MissingConverter diagnostic.
 */
class ConverterConfigTest :
    BehaviorSpec({

        given("a @KMapperConfig with a single custom converter") {
            val converterSource =
                SourceFile.kotlin(
                    "HexConverter.kt",
                    """
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    object HexIntConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                        override fun convertTo(source: String): Int = source.toInt(16)
                        override fun convertFrom(target: Int): String = target.toString(16)
                    }
                    """.trimIndent(),
                )
            val modelSource =
                SourceFile.kotlin(
                    "M.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.KMapperConfig

                    @KMapperConfig(converters = [HexIntConverter::class])
                    object Cfg

                    data class ItemDomainModel(val code: Int)

                    @MapTo(ItemDomainModel::class)
                    data class ItemDataModel(val code: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(listOf(converterSource, modelSource), "ItemDataModelMappers.kt")

                then("the registered converter is auto-discovered for the pair — no field annotation") {
                    generated shouldContain "HexIntConverter.convertTo(it)"
                }
            }
        }

        given("a global converter plus a per-field @ConvertWith override for the SAME type pair") {
            // HEADLINE: the global registry resolves undecorated fields; @ConvertWith(use = …)
            // picks a different converter for one field even though both share (String, Long) —
            // per-field references are exempt from the duplicate-pair validation.
            val converterSource =
                SourceFile.kotlin(
                    "Converters.kt",
                    """
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    /** Global default for the (String, Long) pair. */
                    object IsoInstantConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
                        override fun convertTo(source: String): Long = source.toLong() + 1000L
                        override fun convertFrom(target: Long): String = target.toString()
                    }

                    /** Per-field override: SAME (String, Long) pair, different semantics. */
                    object EpochInstantConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
                        override fun convertTo(source: String): Long = source.toLong()
                        override fun convertFrom(target: Long): String = target.toString()
                    }
                    """.trimIndent(),
                )
            val modelSource =
                SourceFile.kotlin(
                    "EvModel.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.KMapperConfig
                    import com.sahsenvar.kmapper.annotations.ConvertWith

                    @KMapperConfig(converters = [IsoInstantConverter::class])
                    object Cfg

                    data class EventDomainModel(val startsAt: Long, val legacy: Long)

                    @MapTo(EventDomainModel::class)
                    data class EventDataModel(
                        val startsAt: String,
                        @ConvertWith(use = EpochInstantConverter::class) val legacy: String,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(listOf(converterSource, modelSource), "EventDataModelMappers.kt")

                then("the global converter handles the undecorated field") {
                    generated shouldContain "IsoInstantConverter.convertTo(it)"
                }

                then("the per-field override handles the decorated field") {
                    generated shouldContain "EpochInstantConverter.convertTo(it)"
                }
            }
        }

        given("two converters for the same pair inside @KMapperConfig") {
            val converterSource =
                SourceFile.kotlin(
                    "DupConverters.kt",
                    """
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    object ConverterA : MapTypeConverter<String, Long>(String::class, Long::class) {
                        override fun convertTo(source: String): Long = source.toLong()
                        override fun convertFrom(target: Long): String = target.toString()
                    }

                    object ConverterB : MapTypeConverter<String, Long>(String::class, Long::class) {
                        override fun convertTo(source: String): Long = source.toLong() * 2L
                        override fun convertFrom(target: Long): String = target.toString()
                    }
                    """.trimIndent(),
                )
            val modelSource =
                SourceFile.kotlin(
                    "AmbigModel.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.KMapperConfig

                    @KMapperConfig(converters = [ConverterA::class, ConverterB::class])
                    object Cfg

                    data class AmountDomainModel(val value: Long)

                    @MapTo(AmountDomainModel::class)
                    data class AmountDataModel(val value: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("compilation fails — the registry cannot disambiguate undecorated fields") {
                    val messages = errMessages(converterSource, modelSource)
                    messages shouldContain "DUPLICATE"
                }
            }
        }

        given("a type pair with no registered converter at all") {
            val modelSource =
                SourceFile.kotlin(
                    "M3.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class WrappedId(val raw: String)

                    data class XDomainModel(val id: WrappedId)

                    @MapTo(XDomainModel::class)
                    data class XDataModel(val id: Int)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("compilation fails with the MissingConverter guidance") {
                    val messages = errMessages(modelSource)
                    messages shouldContain "has no registered converter"
                    messages shouldContain "@ConvertWith"
                }
            }
        }

        given("a configured converter object extending an abstract parameterized base") {
            // The ledger's official parameterized-converter recipe: the abstract base binds
            // MapTypeConverter<Double, String> with CONCRETE types and takes configuration
            // through its constructor; objects configure by extension. Resolution must walk
            // the superclass CHAIN (the object's direct supertype is the base, not
            // MapTypeConverter).
            val baseAndObjectSource =
                SourceFile.kotlin(
                    "Formatted.kt",
                    """
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    abstract class FormattedDoubleStringConverter(
                        private val digits: Int,
                    ) : MapTypeConverter<Double, String>(Double::class, String::class) {
                        override fun convertTo(source: Double): String {
                            val factor = generateSequence(1L) { it * 10 }.take(digits + 1).last()
                            val scaled = kotlin.math.round(source * factor) / factor
                            return scaled.toString()
                        }

                        override fun convertFrom(target: String): Double = target.toDouble()
                    }

                    object PriceFormatConverter : FormattedDoubleStringConverter(digits = 2)
                    """.trimIndent(),
                )

            `when`("the object is referenced per-field via @ConvertWith(use = …)") {
                val modelSource =
                    SourceFile.kotlin(
                        "PerField.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.ConvertWith
                        import com.sahsenvar.kmapper.annotations.MapTo

                        data class PriceDomainModel(val price: String)

                        @MapTo(PriceDomainModel::class)
                        data class PriceDataModel(
                            @ConvertWith(use = PriceFormatConverter::class) val price: Double,
                        )
                        """.trimIndent(),
                    )
                val generated = okAndReadGenerated(listOf(baseAndObjectSource, modelSource), "PriceDataModelMappers.kt")

                then("the chain-resolved converter is emitted") {
                    generated shouldContain "PriceFormatConverter.convertTo(it)"
                }
            }

            `when`("the object is registered globally via @KMapperConfig") {
                val modelSource =
                    SourceFile.kotlin(
                        "Global.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.KMapperConfig
                        import com.sahsenvar.kmapper.annotations.MapTo

                        @KMapperConfig(converters = [PriceFormatConverter::class])
                        object Cfg

                        data class FareDomainModel(val fare: String)

                        @MapTo(FareDomainModel::class)
                        data class FareDataModel(val fare: Double)
                        """.trimIndent(),
                    )
                val generated = okAndReadGenerated(listOf(baseAndObjectSource, modelSource), "FareDataModelMappers.kt")

                then("the pair registers from the chain and resolves without a field annotation") {
                    generated shouldContain "PriceFormatConverter.convertTo(it)"
                }
            }

            `when`("the converted value lands on a hard site and the mapping runs") {
                val modelSource =
                    SourceFile.kotlin(
                        "Rt.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.KMapperConfig
                        import com.sahsenvar.kmapper.annotations.MapTo

                        @KMapperConfig(converters = [PriceFormatConverter::class])
                        object Cfg

                        data class CostDomainModel(val cost: String)

                        @MapTo(CostDomainModel::class)
                        data class CostDataModel(val cost: Double)
                        """.trimIndent(),
                    )
                val (result, _) = compile(baseAndObjectSource, modelSource)

                then("the configured base behavior (2-digit rounding) applies end-to-end") {
                    val outcome =
                        result.invokeResultMapper(
                            "CostDataModelMappersKt",
                            "toCostDomainModelResult",
                            result.newInstance("CostDataModel", 12.346),
                        )
                    outcome.getOrThrow()!!.prop("cost") shouldBe "12.35"
                }
            }
        }
    })
