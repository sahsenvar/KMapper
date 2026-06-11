package com.sahsenvar.kmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/** Reference-typed fallback so identity (===) assertions are meaningful. */
private class FallbackMarker

class ConversionSeamsTest :
    FunSpec({
        val parse: (String) -> Int = { text -> text.toInt() }
        val parseOrNull: (String) -> Int? = { text -> if (text.isBlank()) null else text.toInt() }

        // beforeEach/afterEach (NOT beforeTest/afterTest): the *Test callbacks also fire for context
        // containers, which re-assigns `recorder` and leaks the container's listener into the global
        // KMapper registry. The *Each callbacks fire for leaf test cases only, keeping add/remove paired.
        lateinit var recorder: RecordingDegradationListener
        beforeEach {
            recorder = RecordingDegradationListener()
            KMapper.addListener(recorder)
        }
        afterEach { KMapper.removeListener(recorder) }

        context("convertOrFail on non-null receiver (hard cell, ladder rows 1/5)") {
            test("ok: returns converted value, silent") {
                "5".convertOrFail("n", "String", "Int", parse) shouldBe 5
                recorder.events shouldBe emptyList()
            }
            test("broken: throws TypeConversionFailed carrying path, pair, and the ORIGINAL cause; nothing reported") {
                val originalCause = IllegalStateException("boom")
                val failure = shouldThrow<MappingException.TypeConversionFailed> {
                    "x".convertOrFail<String, Int>("n", "String", "Int") { _ -> throw originalCause }
                }
                failure.path shouldBe "n"
                failure.from shouldBe "String"
                failure.to shouldBe "Int"
                failure.cause shouldBeSameInstanceAs originalCause
                recorder.events shouldBe emptyList()
            }
            test("inner MappingException propagates path-prefixed, NOT wrapped") {
                val inner = MappingException.RequiredFieldMissing("zipCode")
                val surfaced = shouldThrow<MappingException.RequiredFieldMissing> {
                    "x".convertOrFail<String, Int>("address", "AddressData", "AddressDomain") { _ -> throw inner }
                }
                surfaced.path shouldBe "address.zipCode"
                recorder.events shouldBe emptyList()
            }
            test("deep chain: an already-prefixed inner path gains the outer prefix") {
                val inner = MappingException.RequiredFieldMissing("a.b")
                val surfaced = shouldThrow<MappingException.RequiredFieldMissing> {
                    "x".convertOrFail<String, Int>("outer", "OuterData", "OuterDomain") { _ -> throw inner }
                }
                surfaced.path shouldBe "outer.a.b"
            }
        }

        context("convertOrFail on nullable receiver (hard cell with absence guard)") {
            test("ok") {
                ("5" as String?).convertOrFail("n", "String", "Int", parse) shouldBe 5
                recorder.events shouldBe emptyList()
            }
            test("absent: RequiredFieldMissing with the exact path") {
                val failure = shouldThrow<MappingException.RequiredFieldMissing> {
                    (null as String?).convertOrFail("n", "String", "Int", parse)
                }
                failure.path shouldBe "n"
                recorder.events shouldBe emptyList()
            }
            test("broken: TypeConversionFailed with path, nothing reported") {
                val failure = shouldThrow<MappingException.TypeConversionFailed> {
                    ("abc" as String?).convertOrFail("n", "String", "Int", parse)
                }
                failure.path shouldBe "n"
                recorder.events shouldBe emptyList()
            }
            test("inner MappingException propagates path-prefixed, NOT wrapped") {
                val inner = MappingException.RequiredFieldMissing("zipCode")
                val surfaced = shouldThrow<MappingException.RequiredFieldMissing> {
                    ("x" as String?).convertOrFail<String, Int>("address", "AddressData", "AddressDomain") { _ -> throw inner }
                }
                surfaced.path shouldBe "address.zipCode"
            }
        }

        context("convertOrNull (nullable target, Auto — ladder rows 3/7)") {
            test("ok / absent / sanctioned are silent") {
                ("5" as String?).convertOrNull("n", "String", "Int", parseOrNull) shouldBe 5
                (null as String?).convertOrNull("n", "String", "Int", parseOrNull).shouldBeNull()
                ("" as String?).convertOrNull("n", "String", "Int", parseOrNull).shouldBeNull()
                recorder.events shouldBe emptyList()
            }
            test("broken: null + AbsorbedConversionError whose cause is the TYPED exception") {
                val originalCause = IllegalArgumentException("bad digit")
                ("abc" as String?).convertOrNull<String, Int>("n", "String", "Int") { _ -> throw originalCause }.shouldBeNull()
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                event.path shouldBe "n"
                event.from shouldBe "String"
                event.to shouldBe "Int"
                val typedCause = event.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                typedCause.path shouldBe "n"
                typedCause.from shouldBe "String"
                typedCause.to shouldBe "Int"
                typedCause.cause shouldBeSameInstanceAs originalCause
            }
            test("broken by inner MappingException: absorbed event cause is the path-prefixed typed exception") {
                val inner = MappingException.RequiredFieldMissing("zipCode")
                ("x" as String?).convertOrNull<String, Int>("address", "AddressData", "AddressDomain") { _ -> throw inner }
                    .shouldBeNull()
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                event.path shouldBe "address"
                val typedCause = event.cause.shouldBeInstanceOf<MappingException.RequiredFieldMissing>()
                typedCause.path shouldBe "address.zipCode"
            }
            test("no listener registered: broken still absorbs to null without throwing") {
                KMapper.removeListener(recorder)
                ("abc" as String?).convertOrNull("n", "String", "Int", parseOrNull).shouldBeNull()
                recorder.events shouldBe emptyList()
            }
        }

        context("convertOrNullStrict (nullable target, OnFail.Throw)") {
            test("ok / absent / sanctioned stay soft and silent") {
                ("5" as String?).convertOrNullStrict("n", "String", "Int", parseOrNull) shouldBe 5
                (null as String?).convertOrNullStrict("n", "String", "Int", parseOrNull).shouldBeNull()
                ("" as String?).convertOrNullStrict("n", "String", "Int", parseOrNull).shouldBeNull()
                recorder.events shouldBe emptyList()
            }
            test("broken: rethrows typed, nothing reported") {
                val failure = shouldThrow<MappingException.TypeConversionFailed> {
                    ("abc" as String?).convertOrNullStrict("n", "String", "Int", parseOrNull)
                }
                failure.path shouldBe "n"
                failure.from shouldBe "String"
                failure.to shouldBe "Int"
                recorder.events shouldBe emptyList()
            }
            test("inner MappingException rethrown path-prefixed, type preserved") {
                val inner = MappingException.UnknownEnumValue("status", "Status", "ARCHIVED")
                val surfaced = shouldThrow<MappingException.UnknownEnumValue> {
                    ("x" as String?).convertOrNullStrict<String, Int>("order", "OrderData", "OrderDomain") { _ -> throw inner }
                }
                surfaced.path shouldBe "order.status"
                recorder.events shouldBe emptyList()
            }
        }

        context("convertOrElse (defaulted target, Auto — ladder rows 2/4/6/8)") {
            test("ok: returns converted value, ignores fallback, silent") {
                ("5" as String?).convertOrElse(9, "n", "String", "Int", parseOrNull) shouldBe 5
                recorder.events shouldBe emptyList()
            }
            test("absent: returns the EXACT fallback instance, silent") {
                val fallback = FallbackMarker()
                val resolved =
                    (null as String?).convertOrElse<String, FallbackMarker>(fallback, "n", "String", "FallbackMarker") { _ ->
                        FallbackMarker()
                    }
                resolved shouldBeSameInstanceAs fallback
                recorder.events shouldBe emptyList()
            }
            test("broken: returns the EXACT fallback instance + AbsorbedConversionError with the typed cause") {
                val fallback = FallbackMarker()
                val originalCause = IllegalStateException("boom")
                val resolved =
                    ("x" as String?).convertOrElse<String, FallbackMarker>(fallback, "n", "String", "FallbackMarker") { _ ->
                        throw originalCause
                    }
                resolved shouldBeSameInstanceAs fallback
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                event.path shouldBe "n"
                event.from shouldBe "String"
                event.to shouldBe "FallbackMarker"
                val typedCause = event.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                typedCause.cause shouldBeSameInstanceAs originalCause
            }
            test("sanctioned null: returns the EXACT fallback instance, silent") {
                val fallback = FallbackMarker()
                val resolved =
                    ("x" as String?).convertOrElse<String, FallbackMarker>(fallback, "n", "String", "FallbackMarker") { _ -> null }
                resolved shouldBeSameInstanceAs fallback
                recorder.events shouldBe emptyList()
            }
            test("no listener registered: broken still falls back without throwing") {
                KMapper.removeListener(recorder)
                ("abc" as String?).convertOrElse(9, "n", "String", "Int", parseOrNull) shouldBe 9
                recorder.events shouldBe emptyList()
            }
        }

        context("convertOrElseStrict (defaulted target, OnFail.Throw)") {
            test("ok / absent / sanctioned stay soft and silent; fallback identity preserved") {
                ("5" as String?).convertOrElseStrict(9, "n", "String", "Int", parseOrNull) shouldBe 5
                val fallback = FallbackMarker()
                (null as String?).convertOrElseStrict<String, FallbackMarker>(fallback, "n", "String", "FallbackMarker") { _ ->
                    FallbackMarker()
                } shouldBeSameInstanceAs fallback
                ("x" as String?).convertOrElseStrict<String, FallbackMarker>(fallback, "n", "String", "FallbackMarker") { _ ->
                    null
                } shouldBeSameInstanceAs fallback
                recorder.events shouldBe emptyList()
            }
            test("broken: rethrows typed, nothing reported") {
                val failure = shouldThrow<MappingException.TypeConversionFailed> {
                    ("abc" as String?).convertOrElseStrict(9, "n", "String", "Int", parseOrNull)
                }
                failure.path shouldBe "n"
                recorder.events shouldBe emptyList()
            }
            test("inner MappingException rethrown path-prefixed, type preserved") {
                val inner = MappingException.RequiredFieldMissing("zipCode")
                val surfaced = shouldThrow<MappingException.RequiredFieldMissing> {
                    ("x" as String?).convertOrElseStrict<String, Int>(9, "address", "AddressData", "AddressDomain") { _ ->
                        throw inner
                    }
                }
                surfaced.path shouldBe "address.zipCode"
            }
        }

        context("orRequired (absence guard for seam-less spots)") {
            test("value passes through as the same instance") {
                val marker = FallbackMarker()
                (marker as FallbackMarker?).orRequired("n") shouldBeSameInstanceAs marker
            }
            test("null throws RequiredFieldMissing with the exact path") {
                val failure = shouldThrow<MappingException.RequiredFieldMissing> {
                    (null as Int?).orRequired("user.age")
                }
                failure.path shouldBe "user.age"
            }
        }

        context("properties") {
            test("convertOrFail agrees with the convert function for every non-null receiver") {
                checkAll(Arb.int()) { number ->
                    number.convertOrFail("n", "Int", "Long") { value -> value.toLong() + 1 } shouldBe number.toLong() + 1
                }
            }
            test("convertOrNull never throws and never reports for a total convert") {
                checkAll(Arb.int()) { number ->
                    (number as Int?).convertOrNull("n", "Int", "Long") { value -> value.toLong() } shouldBe number.toLong()
                }
                recorder.events shouldBe emptyList()
            }
        }
    })
