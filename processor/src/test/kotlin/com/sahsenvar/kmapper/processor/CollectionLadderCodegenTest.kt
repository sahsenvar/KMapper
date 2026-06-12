@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.MappingDegradation
import com.sahsenvar.kmapper.MappingException
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Golden + runtime tests for the element-ladder collection codegen (plan Task 16): the
 * convertEach…/convertEntries… seams replace the INTERIM `.map { }` chains, seam selection
 * follows the locked (target element shape × onFail) table, the container ladder stays
 * separate (orRequired / `?: base.x`), and @CollectionWrapper composition runs the element
 * conversion on the normal rails inside wrap()/after unwrap().
 */
class CollectionLadderCodegenTest :
    BehaviorSpec({

        given("List<String> -> List<Long>: built-in element converter on the skip rung") {
            val source =
                SourceFile.kotlin(
                    "ListConverter.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class OrderDomainModel(val tags: List<Long>)

                    @MapTo(OrderDomainModel::class)
                    data class OrderDataModel(val tags: List<String>)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("convertEachOrSkip is emitted with path and element-type FQN literals") {
                    val generated = compilation.generatedFile("OrderDataModelMappers.kt")
                    generated shouldContain "convertEachOrSkip(\"tags\", \"kotlin.String\", \"kotlin.Long\")"
                }

                then("the element converter rides its OrNull method (the seam absorbs nullable returns)") {
                    val generated = compilation.generatedFile("OrderDataModelMappers.kt")
                    generated shouldContain "LongStringConverter.convertFromOrNull(it)"
                }
            }

            `when`("1 element of 3 is broken at runtime") {
                then("the survivors land, the broken element drops with its indexed path") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "OrderDataModelMappersKt",
                                "toOrderDomainModelResult",
                                result.newInstance("OrderDataModel", listOf("7", "broken", "9")),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("tags") shouldBe listOf(7L, 9L)
                        listener.events.shouldHaveSize(1)
                        val event = listener.events.single().shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
                        event.path shouldBe "tags[1]"
                        event.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                    }
                }
            }

            `when`("ALL elements are broken at runtime (accepted extreme)") {
                then("the mapping still succeeds with an empty list and one event per element") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "OrderDataModelMappersKt",
                                "toOrderDomainModelResult",
                                result.newInstance("OrderDataModel", listOf("a", "b", "c")),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        (outcome.getOrNull()!!.prop("tags") as List<*>).shouldBeEmpty()
                        listener.events.shouldHaveSize(3)
                        listener.events.map { it.path } shouldBe listOf("tags[0]", "tags[1]", "tags[2]")
                    }
                }
            }
        }

        given("List<String?> -> List<Long>: source nulls drop on the free filterNotNull rung") {
            val source =
                SourceFile.kotlin(
                    "ListNullSource.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class CleanDomainModel(val tags: List<Long>)

                    @MapTo(CleanDomainModel::class)
                    data class CleanDataModel(val tags: List<String?>)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the same skip seam handles nullable source elements — no extra emission") {
                    val generated = compilation.generatedFile("CleanDataModelMappers.kt")
                    generated shouldContain "convertEachOrSkip(\"tags\", \"kotlin.String\", \"kotlin.Long\")"
                }
            }

            `when`("a null element rides along at runtime") {
                then("it drops with a DroppedNullElement report at the indexed path") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "CleanDataModelMappersKt",
                                "toCleanDomainModelResult",
                                result.newInstance("CleanDataModel", listOf("1", null, "3")),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("tags") shouldBe listOf(1L, 3L)
                        listener.events.shouldHaveSize(1)
                        listener.events.single().shouldBeInstanceOf<MappingDegradation.DroppedNullElement>().path shouldBe "tags[1]"
                    }
                }
            }
        }

        given("List<String> -> List<Long?>: nullable target elements absorb null-in-place") {
            val source =
                SourceFile.kotlin(
                    "ListNullTarget.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class LooseDomainModel(val scores: List<Long?>)

                    @MapTo(LooseDomainModel::class)
                    data class LooseDataModel(val scores: List<String>)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("convertEachOrNull is emitted (alignment-preserving rung)") {
                    val generated = okAndReadGenerated(source, "LooseDataModelMappers.kt")
                    generated shouldContain "convertEachOrNull(\"scores\", \"kotlin.String\", \"kotlin.Long\")"
                }
            }
        }

        given("List<String?> -> List<Long?>: alignment preservation under mixed null/broken input") {
            val source =
                SourceFile.kotlin(
                    "Aligned.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class AlignedDomainModel(val scores: List<Long?>)

                    @MapTo(AlignedDomainModel::class)
                    data class AlignedDataModel(val scores: List<String?>)
                    """.trimIndent(),
                )
            val (result, _) = compile(source)

            `when`("the list carries a clean, a null, and a broken element") {
                then("length and index alignment survive; only the broken element reports") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "AlignedDataModelMappersKt",
                                "toAlignedDomainModelResult",
                                result.newInstance("AlignedDataModel", listOf("1", null, "x")),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("scores") shouldBe listOf(1L, null, null)
                        listener.events.shouldHaveSize(1)
                        val event = listener.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                        event.path shouldBe "scores[2]"
                    }
                }
            }
        }

        given("OnFail.Throw on a converter-backed list field") {
            val source =
                SourceFile.kotlin(
                    "ListThrow.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.ConvertWith
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.OnFail

                    data class StrictDomainModel(val tags: List<Long>)

                    @MapTo(StrictDomainModel::class)
                    data class StrictDataModel(@ConvertWith(onFail = OnFail.Throw) val tags: List<String>)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("convertEachOrFail is emitted with the converter's TOTAL method") {
                    val generated = compilation.generatedFile("StrictDataModelMappers.kt")
                    generated shouldContain "convertEachOrFail(\"tags\", \"kotlin.String\", \"kotlin.Long\")"
                    generated shouldContain "LongStringConverter.convertFrom(it)"
                }
            }

            `when`("an element is broken at runtime") {
                then("the whole mapping fails hard at the indexed path") {
                    val outcome =
                        result.invokeResultMapper(
                            "StrictDataModelMappersKt",
                            "toStrictDomainModelResult",
                            result.newInstance("StrictDataModel", listOf("1", "broken", "3")),
                        )
                    outcome.isFailure.shouldBeTrue()
                    val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                    exception.path shouldBe "tags[1]"
                }
            }
        }

        given("a Set<Long> target fed from Set<String>") {
            val source =
                SourceFile.kotlin(
                    "SetConverter.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class BadgeDomainModel(val codes: Set<Long>)

                    @MapTo(BadgeDomainModel::class)
                    data class BadgeDataModel(val codes: Set<String>)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the Set seam is emitted (always-skip element rung)") {
                    val generated = okAndReadGenerated(source, "BadgeDataModelMappers.kt")
                    generated shouldContain "convertEachOrSkipToSet(\"codes\", \"kotlin.String\", \"kotlin.Long\")"
                }
            }
        }

        given("Map<String, String> -> Map<String, Long>: per-entry ladders with real type pairs") {
            val source =
                SourceFile.kotlin(
                    "MapConverter.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class PriceDomainModel(val prices: Map<String, Long>)

                    @MapTo(PriceDomainModel::class)
                    data class PriceDataModel(val prices: Map<String, String>)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("convertEntriesOrSkip carries the key and value FQN pairs plus the identity key lambda") {
                    val generated = compilation.generatedFile("PriceDataModelMappers.kt")
                    generated shouldContain
                        "convertEntriesOrSkip(\"prices\", \"kotlin.String\", \"kotlin.String\", \"kotlin.String\", \"kotlin.Long\""
                    generated shouldContain "LongStringConverter.convertFromOrNull(it)"
                }
            }

            `when`("one entry's value is broken at runtime") {
                then("the entry drops with the keyed path; the clean entry survives") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "PriceDataModelMappersKt",
                                "toPriceDomainModelResult",
                                result.newInstance("PriceDataModel", mapOf("usd" to "100", "eur" to "broken")),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("prices") shouldBe mapOf("usd" to 100L)
                        listener.events.shouldHaveSize(1)
                        val event = listener.events.single().shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
                        event.path shouldBe "prices[\"eur\"]"
                    }
                }
            }
        }

        given("a nullable source container into a target defaulted with emptyList()") {
            val source =
                SourceFile.kotlin(
                    "NullableContainer.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class TaggedDomainModel(val tags: List<Long> = emptyList())

                    @MapTo(TaggedDomainModel::class)
                    data class TaggedDataModel(val tags: List<String>?)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the chain is safe-called and the copy stage falls back to the base default") {
                    val generated = compilation.generatedFile("TaggedDataModelMappers.kt")
                    generated shouldContain "tags?.convertEachOrSkip(\"tags\", \"kotlin.String\", \"kotlin.Long\")"
                    generated shouldContain "?: base.tags"
                }
            }

            `when`("the source container is absent at runtime") {
                then("the declared default applies SILENTLY (container ladder, not element ladder)") {
                    withRecordingListener { listener ->
                        val outcome =
                            result.invokeResultMapper(
                                "TaggedDataModelMappersKt",
                                "toTaggedDomainModelResult",
                                result.newInstance("TaggedDataModel", null as Any?),
                            )
                        outcome.isSuccess.shouldBeTrue()
                        outcome.getOrNull()!!.prop("tags") shouldBe emptyList<Long>()
                        listener.events.shouldBeEmpty()
                    }
                }
            }
        }

        given("@CollectionWrapper composition: List<String> -> PersistentList<Long>") {
            val source =
                SourceFile.kotlin(
                    "WrapComposition.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.CollectionWrapper
                    import com.sahsenvar.kmapper.annotations.KMapperConfig
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import kotlinx.collections.immutable.PersistentList
                    import kotlinx.collections.immutable.toPersistentList

                    @CollectionWrapper(forType = PersistentList::class)
                    object PersistentListWrapper {
                        fun <T> wrap(source: List<T>): PersistentList<T> = source.toPersistentList()
                    }

                    @KMapperConfig(wrappers = [PersistentListWrapper::class])
                    object MappingConfig

                    data class IdsDomainModel(val ids: PersistentList<Long>)

                    @MapTo(IdsDomainModel::class)
                    data class IdsDataModel(val ids: List<String>)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the wrapper handles only the shell; elements convert on the normal seam rails INSIDE wrap()") {
                    val generated = okAndReadGenerated(source, "IdsDataModelMappers.kt")
                    generated shouldContain "PersistentListWrapper.wrap("
                    generated shouldContain "convertEachOrSkip(\"ids\", \"kotlin.String\", \"kotlin.Long\")"
                }
            }
        }

        given("@CollectionWrapper unwrap direction: Box<ItemDataModel> -> List<ItemDomainModel>") {
            val source =
                SourceFile.kotlin(
                    "UnwrapDirection.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.CollectionWrapper
                    import com.sahsenvar.kmapper.annotations.KMapperConfig
                    import com.sahsenvar.kmapper.annotations.MapTo

                    class Box<T>(val values: List<T>)

                    @CollectionWrapper(forType = Box::class)
                    object BoxWrapper {
                        fun <T> wrap(source: List<T>): Box<T> = Box(source)

                        fun <T> unwrap(source: Box<T>): List<T> = source.values
                    }

                    @KMapperConfig(wrappers = [BoxWrapper::class])
                    object MappingConfig

                    data class ItemDomainModel(val sku: String)

                    @MapTo(ItemDomainModel::class)
                    data class ItemDataModel(val sku: String)

                    data class InventoryDomainModel(val items: List<ItemDomainModel>)

                    @MapTo(InventoryDomainModel::class)
                    data class InventoryDataModel(val items: Box<ItemDataModel>)
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("unwrap() feeds the element seam chain") {
                    val generated = compilation.generatedFile("InventoryDataModelMappers.kt")
                    generated shouldContain "BoxWrapper.unwrap("
                    generated shouldContain "convertEachOrSkip(\"items\", \"ItemDataModel\", \"ItemDomainModel\")"
                }
            }

            `when`("the mapping runs") {
                then("the unwrapped elements convert through the nested mapper") {
                    val item = result.newInstance("ItemDataModel", "SKU-1")
                    val box =
                        result.classLoader
                            .loadClass("Box")
                            .declaredConstructors
                            .first { it.parameterCount == 1 }
                            .newInstance(listOf(item))
                    val outcome =
                        result.invokeResultMapper(
                            "InventoryDataModelMappersKt",
                            "toInventoryDomainModelResult",
                            result.newInstance("InventoryDataModel", box),
                        )
                    outcome.isSuccess.shouldBeTrue()
                    val items = outcome.getOrNull()!!.prop("items") as List<*>
                    items.shouldHaveSize(1)
                    items.single()!!.prop("sku") shouldBe "SKU-1"
                }
            }
        }

        given("a wrapper declaring only wrap, used by a mapping that needs unwrap") {
            val source =
                SourceFile.kotlin(
                    "MissingUnwrap.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.CollectionWrapper
                    import com.sahsenvar.kmapper.annotations.KMapperConfig
                    import com.sahsenvar.kmapper.annotations.MapTo

                    class Box<T>(val values: List<T>)

                    @CollectionWrapper(forType = Box::class)
                    object BoxWrapper {
                        fun <T> wrap(source: List<T>): Box<T> = Box(source)
                    }

                    @KMapperConfig(wrappers = [BoxWrapper::class])
                    object MappingConfig

                    data class ItemDomainModel(val sku: String)

                    @MapTo(ItemDomainModel::class)
                    data class ItemDataModel(val sku: String)

                    data class InventoryDomainModel(val items: List<ItemDomainModel>)

                    @MapTo(InventoryDomainModel::class)
                    data class InventoryDataModel(val items: Box<ItemDataModel>)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the guided missing-direction error names the wrapper and the direction") {
                    val messages = errMessages(source)
                    messages shouldContain "BoxWrapper"
                    messages shouldContain "declares no unwrap"
                }
            }
        }

        given("a @CollectionWrapper whose wrap() returns a type other than forType") {
            val source =
                SourceFile.kotlin(
                    "BadSignature.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.CollectionWrapper
                    import com.sahsenvar.kmapper.annotations.KMapperConfig

                    class Box<T>(val values: List<T>)

                    @CollectionWrapper(forType = Box::class)
                    object BadBoxWrapper {
                        fun <T> wrap(source: List<T>): List<T> = source
                    }

                    @KMapperConfig(wrappers = [BadBoxWrapper::class])
                    object MappingConfig
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the signature error names the expected shape") {
                    errMessages(source) shouldContain "wrap(source: List<T>): Box<T>"
                }
            }
        }
    })
