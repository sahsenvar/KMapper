@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.MappingException
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Converter RESOLUTION suite (plan Task 17): pair-keyed auto-discovery with orientation
 * awareness, the two compile-time error kinds with their declared reasons
 * (UnsupportedConversion with the @UnsupportedDirection text vs MissingConverter), per-field
 * `use=` overrides, direction-scoped directive asymmetry, the dead-'?' warning — plus the
 * carry-along pins: flipped-orientation duplicates, misplaced @UnsupportedDirection,
 * wrong-pair `use=`, generic-passthrough bases, most-derived re-overrides, the
 * (landing shape × Throw) seam goldens, and the UnknownEnumValue path-argument golden.
 */
class ConverterResolutionTest :
    BehaviorSpec({

        // ── Case 1: auto-discovery + orientation ───────────────────────────────────────

        given("an Int source into a Long target with no annotation anywhere") {
            val source =
                SourceFile.kotlin(
                    "Widen.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class AmountDomainModel(val amount: Long)

                    @MapTo(AmountDomainModel::class)
                    data class AmountDataModel(val amount: Int)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the built-in pair resolves in its FROM orientation (Int -> Long widens)") {
                    val generated = okAndReadGenerated(source, "AmountDataModelMappers.kt")
                    generated shouldContain "LongIntConverter.convertFrom(it)"
                    generated shouldContain "convertOrFail(\"amount\", \"kotlin.Int\", \"kotlin.Long\")"
                }
            }
        }

        // ── Case 2: narrowing direction refused with the declared reason ───────────────

        given("a Long source into an Int target (the converter's refused TO direction)") {
            val source =
                SourceFile.kotlin(
                    "Narrow.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class CountDomainModel(val count: Int)

                    @MapTo(CountDomainModel::class)
                    data class CountDataModel(val count: Long)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("compilation fails with the @UnsupportedDirection narrowing reason, not the generic text") {
                    errMessages(source) shouldContain "Long -> Int narrows"
                }
            }
        }

        // ── Case 3: X-pair FROM reason ──────────────────────────────────────────────────

        given("an Int source into a Float target (X-pair, lossy widening refused)") {
            val source =
                SourceFile.kotlin(
                    "Lossy.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class RatioDomainModel(val ratio: Float)

                    @MapTo(RatioDomainModel::class)
                    data class RatioDataModel(val ratio: Int)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("compilation fails with the pair-specific mantissa reason") {
                    errMessages(source) shouldContain "lossy above 2^24"
                }
            }
        }

        // ── Case 4: X-pair reason, NOT MissingConverter ─────────────────────────────────

        given("a Boolean source into an Int target (X-pair object exists, direction refused)") {
            val source =
                SourceFile.kotlin(
                    "BoolInt.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class FlagDomainModel(val flag: Int)

                    @MapTo(FlagDomainModel::class)
                    data class FlagDataModel(val flag: Boolean)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val messages = errMessages(source)

                then("the declared no-canonical-encoding reason surfaces") {
                    messages shouldContain "no canonical"
                }

                then("the pair is NOT reported as missing — the converter object exists") {
                    messages shouldNotContain "has no registered converter"
                }
            }
        }

        // ── Case 5: MissingConverter ────────────────────────────────────────────────────

        given("a String source into a user class with nothing registered") {
            val source =
                SourceFile.kotlin(
                    "Missing.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    class OpaqueToken(val raw: String)

                    data class SessionDomainModel(val token: OpaqueToken)

                    @MapTo(SessionDomainModel::class)
                    data class SessionDataModel(val token: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("compilation fails with the MissingConverter guidance") {
                    errMessages(source) shouldContain "has no registered converter"
                }
            }
        }

        // ── Case 6: per-field use= beats Direct ─────────────────────────────────────────

        given("a same-type String field decorated with @ConvertWith(use = …)") {
            val source =
                SourceFile.kotlin(
                    "Override.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    object SurnameConverter : MapTypeConverter<String, String>(String::class, String::class) {
                        override fun convertTo(source: String): String = source.uppercase()

                        override fun convertFrom(target: String): String = target.lowercase()
                    }

                    data class PersonDomainModel(val surname: String)

                    @MapTo(PersonDomainModel::class)
                    data class PersonDataModel(
                        @ConvertWith(use = SurnameConverter::class) val surname: String,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the override converter is called — the directive beats the Direct same-type strategy") {
                    val generated = okAndReadGenerated(source, "PersonDataModelMappers.kt")
                    generated shouldContain "SurnameConverter.convertTo"
                }
            }
        }

        // ── Case 7: @KMapperConfig discovery without a field annotation ────────────────

        given("a converter registered globally via @KMapperConfig") {
            val source =
                SourceFile.kotlin(
                    "Config.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.KMapperConfig
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    object OccConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                        override fun convertTo(source: String): Int = source.toInt(8)

                        override fun convertFrom(target: Int): String = target.toString(8)
                    }

                    @KMapperConfig(converters = [OccConverter::class])
                    object MappingConfig

                    data class JobDomainModel(val occupation: Int)

                    @MapTo(JobDomainModel::class)
                    data class JobDataModel(val occupation: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the pair auto-discovers from the registry — no per-field annotation needed") {
                    val generated = okAndReadGenerated(source, "JobDataModelMappers.kt")
                    generated shouldContain "OccConverter.convertTo(it)"
                }
            }
        }

        // ── Case 8: @ConvertFrom asymmetry ──────────────────────────────────────────────

        given("a @ConvertFrom(onFail = Throw) directive on the reverse-source field") {
            val source =
                SourceFile.kotlin(
                    "Asymmetry.kt",
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

        // ── Case 9: dead-'?' warning fires and the build stays OK ──────────────────────

        given("a non-null Int source into a nullable Long target (mapping never yields null)") {
            val source =
                SourceFile.kotlin(
                    "DeadNullable.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class LabelDomainModel(val label: Long?)

                    @MapTo(LabelDomainModel::class)
                    data class LabelDataModel(val label: Int)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val (compilationResult, _) = compile(source)

                then("the dead-'?' warning fires") {
                    compilationResult.messages shouldContain "dead '?'"
                }

                then("compilation still succeeds — it is a warning, not an error") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }
            }
        }

        // ── Carry-along: flipped-orientation duplicate pair in @KMapperConfig ──────────

        given("two @KMapperConfig converters declaring the SAME pair in flipped orientations") {
            val source =
                SourceFile.kotlin(
                    "Flipped.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.KMapperConfig
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    object UsdToCodeConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                        override fun convertTo(source: String): Int = source.toInt()

                        override fun convertFrom(target: Int): String = target.toString()
                    }

                    object CodeToUsdConverter : MapTypeConverter<Int, String>(Int::class, String::class) {
                        override fun convertTo(source: Int): String = source.toString()

                        override fun convertFrom(target: String): Int = target.toInt()
                    }

                    @KMapperConfig(converters = [UsdToCodeConverter::class, CodeToUsdConverter::class])
                    object MappingConfig
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val messages = errMessages(source)

                then("the duplicate is caught despite the flipped declaration") {
                    messages shouldContain "DUPLICATE"
                }

                then("the error lists BOTH declared orientations") {
                    messages shouldContain "declared as kotlin.String → kotlin.Int"
                    messages shouldContain "declared as kotlin.Int → kotlin.String"
                }
            }
        }

        // ── Carry-along: @UnsupportedDirection on an OrNull variant (resolution level) ──

        given("a referenced converter that annotates its OrNull variant instead of the total") {
            val source =
                SourceFile.kotlin(
                    "Misannotated.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.converter.MapTypeConverter
                    import com.sahsenvar.kmapper.converter.UnsupportedDirection

                    object MisannotatedConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                        override fun convertTo(source: String): Int = source.toInt()

                        @UnsupportedDirection("misplaced on purpose")
                        override fun convertToOrNull(source: String): Int? = null
                    }

                    data class TallyDomainModel(val tally: Int)

                    @MapTo(TallyDomainModel::class)
                    data class TallyDataModel(
                        @ConvertWith(use = MisannotatedConverter::class) val tally: String,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the guided error says to annotate the total method") {
                    errMessages(source) shouldContain "must annotate the total method"
                }
            }
        }

        // ── Carry-along: wrong-pair use= ────────────────────────────────────────────────

        given("a per-field use= referencing a converter for an unrelated type pair") {
            val source =
                SourceFile.kotlin(
                    "WrongPair.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.converter.MapTypeConverter

                    object TemperatureConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                        override fun convertTo(source: String): Int = source.toInt()

                        override fun convertFrom(target: Int): String = target.toString()
                    }

                    data class GaugeDomainModel(val level: Long)

                    @MapTo(GaugeDomainModel::class)
                    data class GaugeDataModel(
                        @ConvertWith(use = TemperatureConverter::class) val level: Boolean,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the error names the converter's actual pair and the field's pair") {
                    errMessages(source) shouldContain
                        "handles kotlin.String <-> kotlin.Int, not kotlin.Boolean -> kotlin.Long"
                }
            }
        }

        // ── Carry-along: generic-passthrough base ───────────────────────────────────────

        given("a converter reaching MapTypeConverter through a generic-passthrough base") {
            val source =
                SourceFile.kotlin(
                    "Passthrough.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.converter.MapTypeConverter
                    import kotlin.reflect.KClass

                    abstract class StringifyBase<X : Any>(
                        type: KClass<X>,
                    ) : MapTypeConverter<X, String>(type, String::class) {
                        override fun convertTo(source: X): String = source.toString()
                    }

                    object TokenStringConverter : StringifyBase<Int>(Int::class)

                    data class TokenDomainModel(val token: String)

                    @MapTo(TokenDomainModel::class)
                    data class TokenDataModel(
                        @ConvertWith(use = TokenStringConverter::class) val token: Int,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the guided generic-passthrough error tells the author to bind concrete types") {
                    val messages = errMessages(source)
                    messages shouldContain "generic-passthrough"
                    messages shouldContain "Bind the pair with concrete types"
                }
            }
        }

        // ── Carry-along: most-derived re-override wins over the base's refusal ─────────

        given("a derived converter re-overriding a direction its base annotated @UnsupportedDirection") {
            val source =
                SourceFile.kotlin(
                    "ReOverride.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.converter.MapTypeConverter
                    import com.sahsenvar.kmapper.converter.UnsupportedDirection

                    abstract class GuardedBaseConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                        override fun convertFrom(target: Int): String = target.toString()

                        @UnsupportedDirection("base refuses String -> Int")
                        override fun convertTo(source: String): Int = unsupported()
                    }

                    object LiberalConverter : GuardedBaseConverter() {
                        override fun convertTo(source: String): Int = source.toInt()
                    }

                    data class DigitsDomainModel(val digits: Int)

                    @MapTo(DigitsDomainModel::class)
                    data class DigitsDataModel(
                        @ConvertWith(use = LiberalConverter::class) val digits: String,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the re-overridden direction is PROVIDED — the most-derived declaration wins") {
                    val generated = okAndReadGenerated(source, "DigitsDataModelMappers.kt")
                    generated shouldContain "LiberalConverter.convertTo(it)"
                }
            }
        }

        // ── Carry-along: (landing shape × Throw) seam goldens ───────────────────────────

        given("OnFail.Throw against each landing shape") {
            `when`("the target is defaulted (COPY cell)") {
                val source =
                    SourceFile.kotlin(
                        "CopyThrow.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.ConvertWith
                        import com.sahsenvar.kmapper.annotations.MapTo
                        import com.sahsenvar.kmapper.annotations.OnFail

                        data class RetryDomainModel(val retries: Int = 3)

                        @MapTo(RetryDomainModel::class)
                        data class RetryDataModel(@ConvertWith(onFail = OnFail.Throw) val retries: String?)
                        """.trimIndent(),
                    )

                then("convertOrElseStrict is emitted with the base fallback") {
                    val generated = okAndReadGenerated(source, "RetryDataModelMappers.kt")
                    generated shouldContain
                        "convertOrElseStrict(\"retries\", \"kotlin.String\", \"kotlin.Int\", base.retries)"
                }
            }

            `when`("the target is hard (HARD cell — Throw is the identity policy there)") {
                val source =
                    SourceFile.kotlin(
                        "HardThrow.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.ConvertWith
                        import com.sahsenvar.kmapper.annotations.MapTo
                        import com.sahsenvar.kmapper.annotations.OnFail

                        data class TotalDomainModel(val total: Long)

                        @MapTo(TotalDomainModel::class)
                        data class TotalDataModel(@ConvertWith(onFail = OnFail.Throw) val total: String)
                        """.trimIndent(),
                    )

                then("the plain hard seam is still emitted — Throw changes nothing on a hard cell") {
                    val generated = okAndReadGenerated(source, "TotalDataModelMappers.kt")
                    generated shouldContain "convertOrFail(\"total\", \"kotlin.String\", \"kotlin.Long\")"
                }
            }

            `when`("the field is a nested data class on a nullable landing site") {
                val source =
                    SourceFile.kotlin(
                        "NestedThrow.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.ConvertWith
                        import com.sahsenvar.kmapper.annotations.MapTo
                        import com.sahsenvar.kmapper.annotations.OnFail

                        data class AddressDomainModel(val zipCode: Int)

                        data class ContactDomainModel(val address: AddressDomainModel?)

                        @MapTo(AddressDomainModel::class)
                        data class AddressDataModel(val zipCode: String)

                        @MapTo(ContactDomainModel::class)
                        data class ContactDataModel(
                            @ConvertWith(onFail = OnFail.Throw) val address: AddressDataModel?,
                        )
                        """.trimIndent(),
                    )

                then("the strict nullable seam carries the nested sub-mapper lambda") {
                    val generated = okAndReadGenerated(source, "ContactDataModelMappers.kt")
                    generated shouldContain "convertOrNullStrict(\"address\", \"AddressDataModel\", \"AddressDomainModel\")"
                    generated shouldContain "toAddressDomainModelResult().getOrThrow()"
                }
            }
        }

        // ── Carry-along: UnknownEnumValue path golden (target field name) ───────────────

        given("a wire field renamed via @FieldMap feeding a MappableEnum target") {
            val source =
                SourceFile.kotlin(
                    "EnumPath.kt",
                    """
                    import com.sahsenvar.kmapper.MappableEnum
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.annotations.MapTo

                    enum class Status(override val wireValue: String) : MappableEnum<String> {
                        ACTIVE("active"),
                    }

                    data class AccountDomainModel(val status: Status)

                    @MapTo(AccountDomainModel::class)
                    data class AccountDataModel(
                        @FieldMap(fieldName = "status", targetClass = AccountDomainModel::class) val state: String,
                    )
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the seam path literal is the TARGET field name, not the renamed source field") {
                    val generated = compilation.generatedFile("AccountDataModelMappers.kt")
                    generated shouldContain "convertOrFail(\"status\", \"kotlin.String\", \"Status\")"
                    generated shouldContain "UnknownEnumValue"
                    generated shouldNotContain "convertOrFail(\"state\""
                }
            }

            `when`("an unknown wire value arrives at runtime") {
                then("the hard failure carries the target field path") {
                    val outcome =
                        result.invokeResultMapper(
                            "AccountDataModelMappersKt",
                            "toAccountDomainModelResult",
                            result.newInstance("AccountDataModel", "frozen"),
                        )
                    outcome.isFailure.shouldBeTrue()
                    val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.UnknownEnumValue>()
                    exception.path shouldBe "status"
                }
            }
        }
    })
