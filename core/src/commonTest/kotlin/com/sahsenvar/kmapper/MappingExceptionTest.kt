package com.sahsenvar.kmapper

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class MappingExceptionTest :
    FunSpec({

        // ─── RequiredFieldMissing ────────────────────────────────────────────────

        context("RequiredFieldMissing") {
            test("carries path and renders it in the message") {
                val exception = MappingException.RequiredFieldMissing("userId")
                exception.path shouldBe "userId"
                exception.message shouldBe "Required field missing: userId"
            }

            test("withPathPrefix keeps the concrete type and extends the path") {
                val original = MappingException.RequiredFieldMissing("zipCode")
                val prefixed = original.withPathPrefix("address")
                prefixed.shouldBeInstanceOf<MappingException.RequiredFieldMissing>()
                prefixed.path shouldBe "address.zipCode"
                prefixed.message shouldBe "Required field missing: address.zipCode"
            }
        }

        // ─── TypeConversionFailed ────────────────────────────────────────────────

        context("TypeConversionFailed") {
            test("carries path, from, to, and cause; message includes the path") {
                val cause = RuntimeException("bad input")
                val exception = MappingException.TypeConversionFailed("score", "String", "Int", cause)
                exception.path shouldBe "score"
                exception.from shouldBe "String"
                exception.to shouldBe "Int"
                exception.cause shouldBeSameInstanceAs cause
                exception.message shouldBe "Cannot convert score: String -> Int"
            }

            test("empty path renders without a dangling separator") {
                val exception =
                    MappingException.TypeConversionFailed("", "String", "Int", RuntimeException())
                exception.message shouldBe "Cannot convert String -> Int"
                exception.message shouldNotContain ": String"
            }

            test("withPathPrefix keeps type and preserves from, to, and cause") {
                val cause = NumberFormatException("not a number")
                val original = MappingException.TypeConversionFailed("price", "String", "Double", cause)
                val prefixed = original.withPathPrefix("items[3]")
                prefixed.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                prefixed.path shouldBe "items[3].price"
                prefixed.from shouldBe "String"
                prefixed.to shouldBe "Double"
                prefixed.cause shouldBeSameInstanceAs cause
                prefixed.message shouldBe "Cannot convert items[3].price: String -> Double"
            }

            test("withPathPrefix on an empty path upgrades the message to include the path") {
                val pathless =
                    MappingException.TypeConversionFailed("", "String", "Int", RuntimeException())
                val prefixed = pathless.withPathPrefix("score")
                prefixed.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                prefixed.path shouldBe "score"
                prefixed.message shouldBe "Cannot convert score: String -> Int"
            }
        }

        // ─── UnknownEnumValue ────────────────────────────────────────────────────

        context("UnknownEnumValue") {
            test("carries path, enum name, and raw value") {
                val exception = MappingException.UnknownEnumValue("status", "Status", "INVALID")
                exception.path shouldBe "status"
                exception.enum shouldBe "Status"
                exception.value shouldBe "INVALID"
                exception.message shouldContain "Status"
                exception.message shouldContain "INVALID"
                exception.message shouldContain "status"
            }

            test("empty path renders without a dangling ' at ' suffix") {
                val exception = MappingException.UnknownEnumValue("", "Status", "INVALID")
                exception.message shouldBe "Unknown wire value 'INVALID' for enum Status"
                exception.message shouldNotContain " at "
            }

            test("non-string raw value is preserved as-is") {
                val exception = MappingException.UnknownEnumValue("color", "Color", 99)
                exception.value shouldBe 99
                exception.message shouldContain "99"
            }

            test("withPathPrefix keeps type and preserves enum and value") {
                val original = MappingException.UnknownEnumValue("status", "Status", "WAT")
                val prefixed = original.withPathPrefix("order")
                prefixed.shouldBeInstanceOf<MappingException.UnknownEnumValue>()
                prefixed.path shouldBe "order.status"
                prefixed.enum shouldBe "Status"
                prefixed.value shouldBe "WAT"
            }
        }

        // ─── EmptyCollection ─────────────────────────────────────────────────────

        context("EmptyCollection") {
            test("carries path and detail; message includes both") {
                val exception =
                    MappingException.EmptyCollection("tags", "tags must have at least one entry")
                exception.path shouldBe "tags"
                exception.detail shouldBe "tags must have at least one entry"
                exception.message shouldContain "tags must have at least one entry"
                exception.message shouldContain "(at tags)"
            }

            test("empty path renders without a dangling location suffix") {
                val exception = MappingException.EmptyCollection("", "NonEmptyList source was empty")
                exception.message shouldBe "Collection cannot be empty: NonEmptyList source was empty"
                exception.message shouldNotContain "(at "
            }

            test("withPathPrefix keeps type and preserves detail") {
                val original = MappingException.EmptyCollection("roles", "roles was empty")
                val prefixed = original.withPathPrefix("user")
                prefixed.shouldBeInstanceOf<MappingException.EmptyCollection>()
                prefixed.path shouldBe "user.roles"
                prefixed.detail shouldBe "roles was empty"
            }
        }

        // ─── ValidationFailed ────────────────────────────────────────────────────

        context("ValidationFailed") {
            test("carries path and reason; message format is stable") {
                val exception = MappingException.ValidationFailed("email", "must be a valid email")
                exception.path shouldBe "email"
                exception.reason shouldBe "must be a valid email"
                exception.message shouldBe "Validation failed for 'email': must be a valid email"
            }

            test("withPathPrefix keeps type and preserves reason") {
                val original = MappingException.ValidationFailed("name", "must not be blank")
                val prefixed = original.withPathPrefix("customer")
                prefixed.shouldBeInstanceOf<MappingException.ValidationFailed>()
                prefixed.path shouldBe "customer.name"
                prefixed.reason shouldBe "must not be blank"
                prefixed.message shouldBe "Validation failed for 'customer.name': must not be blank"
            }
        }

        // ─── UnsupportedConversion (path-less by design) ─────────────────────────

        context("UnsupportedConversion") {
            test("path is always empty") {
                val exception = MappingException.UnsupportedConversion("Long -> Int is unsupported")
                exception.path shouldBe ""
            }

            test("withPathPrefix is a no-op returning the same instance") {
                val exception = MappingException.UnsupportedConversion("Long -> Int is unsupported")
                exception.withPathPrefix("order") shouldBeSameInstanceAs exception
                exception.path shouldBe ""
            }

            test("message passes through untouched") {
                val builtMessage = unsupportedConversionMessage("Long", "Int")
                MappingException.UnsupportedConversion(builtMessage).message shouldBe builtMessage
            }
        }

        // ─── joinPath rules (observed through withPathPrefix) ────────────────────

        context("path joining") {
            test("plain segments join with a dot") {
                MappingException.RequiredFieldMissing("zipCode")
                    .withPathPrefix("address")
                    .path shouldBe "address.zipCode"
            }

            test("index segment joins WITHOUT a dot") {
                MappingException.RequiredFieldMissing("[3]")
                    .withPathPrefix("items")
                    .path shouldBe "items[3]"
            }

            test("prefixing a path that already carries an index keeps the index intact") {
                MappingException.RequiredFieldMissing("items[3]")
                    .withPathPrefix("order")
                    .path shouldBe "order.items[3]"
            }

            test("prefixing an empty path yields just the prefix") {
                MappingException.TypeConversionFailed("", "String", "Int", RuntimeException())
                    .withPathPrefix("items")
                    .path shouldBe "items"
            }

            test("an empty prefix is a no-op leaving the path unchanged") {
                MappingException.RequiredFieldMissing("zip")
                    .withPathPrefix("")
                    .path shouldBe "zip"
            }

            test("multi-level chaining puts the outermost prefix first") {
                MappingException.RequiredFieldMissing("zipCode")
                    .withPathPrefix("a")
                    .withPathPrefix("b")
                    .path shouldBe "b.a.zipCode"
            }

            test("prefixing a path that starts with an index after a previous chain") {
                MappingException.RequiredFieldMissing("[3].price")
                    .withPathPrefix("items")
                    .path shouldBe "items[3].price"
            }

            test("property: any plain prefix + plain path joins as prefix.path") {
                val plainSegment = Arb.string(minSize = 1, maxSize = 16, codepoints = Codepoint.az())
                checkAll(plainSegment, plainSegment) { prefix, fieldPath ->
                    MappingException.RequiredFieldMissing(fieldPath)
                        .withPathPrefix(prefix)
                        .path shouldBe "$prefix.$fieldPath"
                }
            }

            test("property: chaining prefixes preserves outermost-first ordering") {
                val plainSegment = Arb.string(minSize = 1, maxSize = 16, codepoints = Codepoint.az())
                checkAll(plainSegment, plainSegment, plainSegment) { outer, inner, fieldPath ->
                    MappingException.ValidationFailed(fieldPath, "reason")
                        .withPathPrefix(inner)
                        .withPathPrefix(outer)
                        .path shouldBe "$outer.$inner.$fieldPath"
                }
            }
        }

        // ─── Converter error message builders ────────────────────────────────────

        context("converter error message builders") {
            test("unsupportedConversionMessage names the pair and the policy") {
                val message = unsupportedConversionMessage("Long", "Int")
                message shouldContain "Long -> Int conversion is unsupported"
                message shouldContain "lossy"
            }

            test("missingConverterMessage names the pair and the remedy") {
                val message = missingConverterMessage("String", "OccDomainModel")
                message shouldContain "String -> OccDomainModel"
                message shouldContain "has no registered converter"
            }
        }

        // ─── Sealed-family sanity ────────────────────────────────────────────────

        context("sealed family") {
            val allSubtypes: List<MappingException> =
                listOf(
                    MappingException.RequiredFieldMissing("id"),
                    MappingException.TypeConversionFailed("id", "String", "Int", RuntimeException()),
                    MappingException.UnknownEnumValue("status", "Status", "BAD"),
                    MappingException.EmptyCollection("tags", "tags was empty"),
                    MappingException.ValidationFailed("email", "must be valid"),
                    MappingException.UnsupportedConversion("unsupported"),
                )

            test("every subtype keeps its concrete type through withPathPrefix") {
                allSubtypes.forEach { exception ->
                    val prefixed = exception.withPathPrefix("root")
                    prefixed::class shouldBe exception::class
                }
            }
        }
    })
