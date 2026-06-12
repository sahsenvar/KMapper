@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.KMapper
import com.sahsenvar.kmapper.MappingDegradation
import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.MappingListener
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** Local recording listener (mirrors ScalarLadderCodegenTest's — file-private there). */
private class NestedRecordingListener : MappingListener {
    val events = mutableListOf<MappingDegradation>()

    override fun onDegradation(event: MappingDegradation) {
        events.add(event)
    }
}

private inline fun <T> recordingDegradations(block: (NestedRecordingListener) -> T): T {
    val listener = NestedRecordingListener()
    KMapper.addListener(listener)
    return try {
        block(listener)
    } finally {
        KMapper.removeListener(listener)
    }
}

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
                    recordingDegradations { listener ->
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
    })
