@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Basic generated-source shapes in the Result-boundary world: nested mapping with the
 * required-field absence guard, reverse generation via @MapFrom, and built-in converter
 * resolution (richer-first naming — String→Int rides IntStringConverter's convertFrom).
 */
class BasicMappingTest :
    BehaviorSpec({

        given("a nested mapping with nullable wire fields and non-null domain fields") {
            val source =
                SourceFile.kotlin(
                    "Models.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class AddressDomainModel(val city: String)
                    data class UserDomainModel(val id: String, val address: AddressDomainModel)

                    @MapTo(AddressDomainModel::class)
                    data class AddressDataModel(val city: String?)

                    @MapTo(UserDomainModel::class)
                    data class UserDataModel(val id: String?, val address: AddressDataModel?)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "UserDataModelMappers.kt")

                then("the function rides the Result boundary") {
                    generated shouldContain "fun UserDataModel.toUserDomainModelResult(): Result<UserDomainModel>"
                }

                then("the nullable same-type field gets the absence guard") {
                    generated shouldContain "id.orRequired(\"id\")"
                }

                then("the nullable nested field rides the hard seam with the sub-mapper") {
                    generated shouldContain "convertOrFail(\"address\", \"AddressDataModel\", \"AddressDomainModel\")"
                    generated shouldContain "toAddressDomainModelResult().getOrThrow()"
                }
            }
        }

        given("a reverse mapping declared via @MapFrom") {
            val source =
                SourceFile.kotlin(
                    "Rev.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapFrom

                    data class TagDomainModel(val name: String)

                    @MapFrom(TagDomainModel::class)
                    data class TagDataModel(val name: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "TagDomainModelMappers.kt")

                then("the reverse function is generated on the @MapFrom argument class") {
                    generated shouldContain "fun TagDomainModel.toTagDataModelResult(): Result<TagDataModel>"
                }
            }
        }

        given("a built-in String to Int conversion") {
            val source =
                SourceFile.kotlin(
                    "Conv.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class CountDomainModel(val n: Int)

                    @MapTo(CountDomainModel::class)
                    data class CountDataModel(val n: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "CountDataModelMappers.kt")

                then("the richer-first converter is called in its reverse orientation") {
                    // IntStringConverter is MapTypeConverter<Int, String>; the String→Int field
                    // pair matches T→S, so codegen calls convertFrom.
                    generated shouldContain "IntStringConverter.convertFrom(it)"
                    generated shouldContain "convertOrFail(\"n\", \"kotlin.String\", \"kotlin.Int\")"
                }
            }
        }
    })
