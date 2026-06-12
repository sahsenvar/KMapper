@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.MappingDegradation
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Enum elements inside collections ride the SAME element seams as scalar enum fields:
 * the convert lambda for a wire→enum element is the MappableEnum entries lookup (mirroring
 * the scalar EnumFromWire emission), so `List<String> -> List<Status>` lands on
 * convertEachOr* — an unknown wire value skips + reports under Auto instead of failing as a
 * raw generated-code compile error. The enum→wire direction mirrors the scalar
 * `wireValue` read as the element lambda.
 */
class EnumCollectionMappingTest :
    BehaviorSpec({

        val enumDeclaration =
            """
            import com.sahsenvar.kmapper.MappableEnum
            import com.sahsenvar.kmapper.annotations.MapTo

            enum class Status(override val wireValue: String) : MappableEnum<String> {
                PENDING("PENDING"), SHIPPED("in_transit");
            }
            """.trimIndent()

        given("a List<String> wire field mapped into a List<Status> enum field") {
            val wireToEnumSource =
                SourceFile.kotlin(
                    "EnumListModels.kt",
                    """
                    $enumDeclaration

                    data class OrderDomainModel(val statuses: List<Status>)

                    @MapTo(OrderDomainModel::class)
                    data class OrderDataModel(val statuses: List<String>)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, compilation) = compile(wireToEnumSource)

                then("compilation succeeds — no raw generated-code failure") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("the elements ride the skip seam with the entries-lookup lambda") {
                    val generated = compilation.generatedFile("OrderDataModelMappers.kt")
                    generated shouldContain "convertEachOrSkip(\"statuses\""
                    generated shouldContain "entries.firstOrNull"
                    generated shouldContain "UnknownEnumValue"
                }
            }

            `when`("the mapper runs with one unknown wire value among three") {
                val (compilationResult, _) = compile(wireToEnumSource)
                compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

                val dataModel =
                    compilationResult.newInstance(
                        "OrderDataModel",
                        listOf("PENDING", "definitely_not_a_status", "in_transit"),
                    )

                then("known wires map, the unknown wire skips and reports DroppedBrokenElement") {
                    withRecordingListener { listener ->
                        val domainModel =
                            compilationResult
                                .invokeResultMapper("OrderDataModelMappersKt", "toOrderDomainModelResult", dataModel)
                                .getOrThrow()
                        checkNotNull(domainModel)
                        val statuses = (domainModel.prop("statuses") as Iterable<*>).toList()
                        statuses.map { it.toString() } shouldBe listOf("PENDING", "SHIPPED")

                        listener.events shouldHaveSize 1
                        val degradation = listener.events.single()
                        degradation.shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
                        degradation.path shouldBe "statuses[1]"
                    }
                }
            }
        }

        given("a List<Status> enum field mapped into a List<String> wire field") {
            val enumToWireSource =
                SourceFile.kotlin(
                    "EnumToWireListModels.kt",
                    """
                    $enumDeclaration

                    data class OrderWireModel(val statuses: List<String>)

                    @MapTo(OrderWireModel::class)
                    data class OrderDomainModel(val statuses: List<Status>)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled") {
                val (compilationResult, compilation) = compile(enumToWireSource)

                then("compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("the elements ride the seam with the wireValue read as the lambda") {
                    val generated = compilation.generatedFile("OrderDomainModelMappers.kt")
                    generated shouldContain "convertEachOrSkip(\"statuses\""
                    generated shouldContain "wireValue"
                }
            }
        }
    })
