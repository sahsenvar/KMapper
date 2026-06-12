@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Compile-only (generated-source) tests for the field-anchored @Validate codegen.
 *
 * A field's validators fire whenever it participates in a mapping: annotated on the SOURCE
 * field they run on the source value BEFORE conversion; annotated on the TARGET field they
 * run on `__result` AFTER. (Replaces the old mapping-side @ValidateFrom/@ValidateTo pair.)
 * Each test inspects the KSP-generated .kt file for the expected emission shape.
 */
class ValidateCodegenTest :
    BehaviorSpec({

        given("@Validate on a non-null SOURCE field") {
            val source =
                SourceFile.kotlin(
                    "VF1.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.Validate
                    import com.sahsenvar.kmapper.validation.Validator

                    data class NameDomainModel(val name: String)

                    object TestNotBlank : Validator<String>(String::class) {
                        override fun validate(value: String): String? =
                            if (value.isBlank()) "must not be blank" else null
                    }

                    @MapTo(NameDomainModel::class)
                    data class NameDataModel(
                        @Validate(TestNotBlank::class) val name: String
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "NameDataModelMappers.kt")

                then("a run block fires the validator directly on the non-null source value") {
                    generated shouldContain "run {"
                    generated shouldContain "TestNotBlank.validate"
                    generated shouldContain "ValidationFailed"
                    // Non-null form: no ?.let wrapper around the validate call
                    generated shouldNotContain "name?.let { __s ->"
                }
            }
        }

        given("@Validate on a nullable SOURCE field whose target declares a default") {
            val source =
                SourceFile.kotlin(
                    "VF2.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.Validate
                    import com.sahsenvar.kmapper.validation.Validator

                    data class TagDomainModel(val tag: String = "unknown")

                    object TestNotBlank : Validator<String>(String::class) {
                        override fun validate(value: String): String? =
                            if (value.isBlank()) "must not be blank" else null
                    }

                    @MapTo(TagDomainModel::class)
                    data class TagDataModel(
                        @Validate(TestNotBlank::class)
                        val tag: String?
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "TagDataModelMappers.kt")

                then("the nullable guard skips validation for absent values") {
                    generated shouldContain "?.let { __s ->"
                    generated shouldContain "TestNotBlank.validate"
                    generated shouldContain "ValidationFailed"
                }

                then("the defaulted target still rides the copy stage") {
                    generated shouldContain "base.copy("
                }
            }
        }

        given("@Validate on a non-null TARGET field") {
            // Old-world @ValidateTo (declared on the source field, fired on the result) is now
            // anchored on the TARGET field — semantic relocation, same emission intent.
            val source =
                SourceFile.kotlin(
                    "VT1.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.Validate
                    import com.sahsenvar.kmapper.validation.Validator

                    object TestNotEmpty : Validator<String>(String::class) {
                        override fun validate(value: String): String? =
                            if (value.isEmpty()) "must not be empty" else null
                    }

                    data class EmailDomainModel(
                        @Validate(TestNotEmpty::class) val email: String
                    )

                    @MapTo(EmailDomainModel::class)
                    data class EmailDataModel(val email: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "EmailDataModelMappers.kt")

                then("the validator fires on the captured __result after the mapping expr") {
                    generated shouldContain "run {"
                    generated shouldContain "val __result ="
                    generated shouldContain "TestNotEmpty.validate"
                    generated shouldContain "ValidationFailed"
                    // non-null target: no ?.let { __r -> guard
                    generated shouldNotContain "__result?.let { __r ->"
                }
            }
        }

        given("a field with no validation annotations") {
            val source =
                SourceFile.kotlin(
                    "NoVal.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class PlainDomainModel(val value: String)

                    @MapTo(PlainDomainModel::class)
                    data class PlainDataModel(val value: String)
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "PlainDataModelMappers.kt")

                then("no validation scaffolding is emitted — zero-cost passthrough") {
                    generated shouldNotContain "run {"
                    generated shouldNotContain "ValidationFailed"
                }
            }
        }

        given("@Validate on a source field renamed via @FieldMap") {
            val source =
                SourceFile.kotlin(
                    "TargetName.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.MapTo
                    import com.sahsenvar.kmapper.annotations.Validate
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.validation.Validator

                    data class OrderDomainModel(val orderId: String)

                    object TestNotBlank : Validator<String>(String::class) {
                        override fun validate(value: String): String? =
                            if (value.isBlank()) "must not be blank" else null
                    }

                    @MapTo(OrderDomainModel::class)
                    data class OrderDataModel(
                        @FieldMap(fieldName = "orderId", targetClass = OrderDomainModel::class)
                        @Validate(TestNotBlank::class)
                        val id: String
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                val generated = okAndReadGenerated(source, "OrderDataModelMappers.kt")

                then("the thrown ValidationFailed carries the TARGET field name") {
                    // The exception path arg must be "orderId" (target), not "id" (source)
                    generated shouldContain "\"orderId\""
                }
            }
        }
    })
