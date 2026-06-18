@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.MappingDegradation
import com.sahsenvar.kmapper.MappingException
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Golden + runtime tests for nested mapping through the ladder seams (plan Task 15):
 * sub-mapper rides the seams as `{ it.toXResult().getOrThrow() }`, hard failures accumulate
 * deep paths via withPathPrefix, nullable/defaulted outer fields absorb inner hard failures.
 */
class NestedLadderCodegenTest :
    BehaviorSpec({

        given("a nested data-class field on a hard landing site") {
            val source =
                SourceFile.kotlin(
                    "Nested.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class AddressDomainModel(val zipCode: Int)
                    data class OrderDomainModel(val address: AddressDomainModel, val note: String = "-")

                    @MapTo(AddressDomainModel::class)
                    data class AddressDataModel(val zipCode: String)

                    @MapTo(OrderDomainModel::class)
                    data class OrderDataModel(val address: AddressDataModel, val note: String?)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the sub-mapper rides the hard seam with getOrThrow and simple-name type literals") {
                    val generated = compilation.generatedFile("OrderDataModelMappers.kt")
                    generated shouldContain "toAddressDomainModelResult().getOrThrow()"
                    generated shouldContain "convertOrFail(\"address\", \"AddressDataModel\", \"AddressDomainModel\")"
                }

                then("the defaulted scalar sibling still goes through the copy stage") {
                    val generated = compilation.generatedFile("OrderDataModelMappers.kt")
                    generated shouldContain "base.copy("
                    generated shouldContain "note ?: base.note"
                }
            }

            `when`("the mapping runs with a broken deep field") {
                then("the failure path accumulates to address.zipCode") {
                    val outcome =
                        result.invokeResultMapper(
                            "OrderDataModelMappersKt",
                            "toOrderDomainModelResult",
                            result.newInstance(
                                "OrderDataModel",
                                result.newInstance("AddressDataModel", "not-a-zip"),
                                null as String?,
                            ),
                        )
                    outcome.isFailure.shouldBeTrue()
                    val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException>()
                    exception.path shouldBe "address.zipCode"
                }
            }

            `when`("the mapping runs with a clean deep field") {
                then("the nested object converts and the default fills the absent note") {
                    val outcome =
                        result.invokeResultMapper(
                            "OrderDataModelMappersKt",
                            "toOrderDomainModelResult",
                            result.newInstance(
                                "OrderDataModel",
                                result.newInstance("AddressDataModel", "34000"),
                                null as String?,
                            ),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    val domain = outcome.getOrNull()!!
                    domain.prop("address")!!.prop("zipCode") shouldBe 34000
                    domain.prop("note") shouldBe "-"
                }
            }
        }

        given("a nested target field with a constructor default (copy stage)") {
            val source =
                SourceFile.kotlin(
                    "NestedCopy.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class AddressDomainModel(val zipCode: Int)
                    data class HomeDomainModel(val address: AddressDomainModel = AddressDomainModel(0))

                    @MapTo(AddressDomainModel::class)
                    data class AddressDataModel(val zipCode: String)

                    @MapTo(HomeDomainModel::class)
                    data class HomeDataModel(val address: AddressDataModel?)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "HomeDataModelMappers.kt")

                then("the nested call rides convertOrElse with the base default as fallback") {
                    generated shouldContain
                        "convertOrElse(\"address\", \"AddressDataModel\", \"AddressDomainModel\", base.address)"
                    generated shouldContain "toAddressDomainModelResult().getOrThrow()"
                }
            }
        }

        given("a nested field absorbed at a nullable landing site") {
            val source =
                SourceFile.kotlin(
                    "NestedNullable.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class AddressDomainModel(val zipCode: Int)
                    data class ContactDomainModel(val address: AddressDomainModel?)

                    @MapTo(AddressDomainModel::class)
                    data class AddressDataModel(val zipCode: String)

                    @MapTo(ContactDomainModel::class)
                    data class ContactDataModel(val address: AddressDataModel?)
                    """.trimIndent(),
                )
            val (result, _) = compile(source)

            `when`("the inner mapping fails hard but the outer field is nullable") {
                then("the outer field absorbs to null and reports with the prefixed cause path") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "ContactDataModelMappersKt",
                                "toContactDomainModelResult",
                                result.newInstance(
                                    "ContactDataModel",
                                    result.newInstance("AddressDataModel", "oops"),
                                ),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("address").shouldBeNull()
                        listener.events.shouldHaveSize(1)
                        val event = listener.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                        event.path shouldBe "address"
                        event.cause.shouldBeInstanceOf<MappingException>().path shouldBe "address.zipCode"
                    }
                }
            }
        }

        // Regression: issue #20 — a singular NULLABLE nested @MapTo field that ALSO carries a
        // constructor default (`val bar: BarModel? = null`) lands in the copy stage, where the
        // seam fallback was `base.bar` (statically `BarModel?`). The `convertOrElse` fallback
        // parameter was constrained to `T : Any`, so the GENERATED code failed to compile with
        // "actual type is 'BarModel?', but 'Any' was expected". A nullable LIST escaped because
        // chain landings use `?: base.x` (elvis), not the seam's fallback parameter.
        given("a singular nullable nested target field WITH a constructor default (issue #20)") {
            val source =
                SourceFile.kotlin(
                    "NullableNestedDefault.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class BarDomainModel(val v: Int? = null)
                    data class FooDomainModel(val bar: BarDomainModel? = null)

                    @MapTo(BarDomainModel::class)
                    data class BarDataModel(val v: Int? = null)

                    @MapTo(FooDomainModel::class)
                    data class FooDataModel(val bar: BarDataModel? = null)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the generated mapper compiles (no 'Any' vs nullable-fallback mismatch)") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("the nullable nested field rides convertOrElse with the nullable base default") {
                    compilation.generatedFile("FooDataModelMappers.kt") shouldContain
                        "convertOrElse(\"bar\", \"BarDataModel\", \"BarDomainModel\", base.bar)"
                }
            }

            `when`("a present nested value is mapped") {
                then("it maps through to the domain nested model") {
                    val outcome =
                        result.invokeResultMapper(
                            "FooDataModelMappersKt",
                            "toFooDomainModelResult",
                            result.newInstance("FooDataModel", result.newInstance("BarDataModel", 7)),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("bar")!!.prop("v") shouldBe 7
                }
            }

            `when`("the nested source is null") {
                then("the nullable target lands as null (declared default = null)") {
                    val outcome =
                        result.invokeResultMapper(
                            "FooDataModelMappersKt",
                            "toFooDomainModelResult",
                            result.newInstance("FooDataModel", null as Any?),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    outcome.getOrNull()!!.prop("bar").shouldBeNull()
                }
            }
        }
    })
