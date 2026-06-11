package com.sahsenvar.kmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll

class CollectionSeamsTest :
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

        context("convertEachOrSkip — List<T> default (skip rung)") {
            test("drops null (reported) and broken (reported) in index order, keeps relative order, sanctioned drops silently") {
                listOf("1", null, "abc", "4", "").convertEachOrSkip("tags", "String", "Int", parseOrNull) shouldBe
                    listOf(1, 4)
                recorder.events.map { event -> event::class.simpleName to event.path } shouldBe listOf(
                    "DroppedNullElement" to "tags[1]",
                    "DroppedBrokenElement" to "tags[2]",
                )
            }
            test("broken element's event cause is the TYPED TypeConversionFailed carrying the pair (pair-visible metrics)") {
                val originalCause = IllegalStateException("boom")
                listOf("1", "x").convertEachOrSkip<String, Int>("tags", "String", "Int") { element ->
                    if (element == "x") throw originalCause else element.toInt()
                } shouldBe listOf(1)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
                event.path shouldBe "tags[1]"
                val typedCause = event.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
                typedCause.path shouldBe "tags[1]"
                typedCause.from shouldBe "String"
                typedCause.to shouldBe "Int"
                typedCause.cause shouldBeSameInstanceAs originalCause
            }
            test("nested-mapper element: inner RequiredFieldMissing surfaces in the event prefixed with the element path") {
                val inner = MappingException.RequiredFieldMissing("zip")
                listOf("a", "b", "broken").convertEachOrSkip<String, Int>("items", "AddressData", "AddressDomain") { element ->
                    if (element == "broken") throw inner else element.length
                } shouldBe listOf(1, 1)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
                val typedCause = event.cause.shouldBeInstanceOf<MappingException.RequiredFieldMissing>()
                typedCause.path shouldBe "items[2].zip"
            }
            test("extreme salvage: ALL elements broken -> empty list + one event per element") {
                listOf("a", "b", "c").convertEachOrSkip("xs", "String", "Int", parseOrNull) shouldBe emptyList()
                recorder.events.map { event -> event.path } shouldBe listOf("xs[0]", "xs[1]", "xs[2]")
                recorder.events.forEach { event -> event.shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>() }
            }
        }

        context("convertEachOrNull — List<T?> default (alignment preserved)") {
            test("null passes silently, broken nulls in place with report, length preserved") {
                listOf("1", null, "abc").convertEachOrNull("xs", "String", "Int", parseOrNull) shouldBe
                    listOf(1, null, null)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                event.path shouldBe "xs[2]"
                event.from shouldBe "String"
                event.to shouldBe "Int"
                event.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
            }
        }

        context("convertEachOrFail — OnFail.Throw on List<T>") {
            test("broken element is hard with indexed path") {
                val failure = shouldThrow<MappingException.TypeConversionFailed> {
                    listOf("1", "abc").convertEachOrFail("xs", "String", "Int", parse)
                }
                failure.path shouldBe "xs[1]"
                recorder.events shouldBe emptyList()
            }
            test("null element still skips with report (absence stays type-driven under Throw)") {
                listOf("1", null, "2").convertEachOrFail("xs", "String", "Int", parse) shouldBe listOf(1, 2)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.DroppedNullElement>()
                event.path shouldBe "xs[1]"
            }
            test("inner MappingException propagates path-prefixed with the element index, NOT wrapped") {
                val inner = MappingException.RequiredFieldMissing("zip")
                val surfaced = shouldThrow<MappingException.RequiredFieldMissing> {
                    listOf("ok", "ok", "broken").convertEachOrFail<String, Int>("items", "AddressData", "AddressDomain") { element ->
                        if (element == "broken") throw inner else element.length
                    }
                }
                surfaced.path shouldBe "items[2].zip"
            }
        }

        context("convertEachOrNullStrict — OnFail.Throw on List<T?>") {
            test("broken hard with indexed path, null passes silently") {
                val failure = shouldThrow<MappingException.TypeConversionFailed> {
                    listOf("abc").convertEachOrNullStrict("xs", "String", "Int", parseOrNull)
                }
                failure.path shouldBe "xs[0]"
                listOf("1", null).convertEachOrNullStrict("xs", "String", "Int", parseOrNull) shouldBe listOf(1, null)
                recorder.events shouldBe emptyList()
            }
        }

        context("convertEachOrSkipToSet — Set always skips") {
            test("post-conversion convergence reported ONCE with the LATER element's path") {
                listOf("01", "1").convertEachOrSkipToSet("ids", "String", "Int", parseOrNull) shouldBe setOf(1)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.ConvergedDuplicateElement>()
                event.path shouldBe "ids[1]"
            }
            test("null and broken elements reported as in lists; survivors keep insertion order") {
                val survivors = listOf("3", null, "abc", "1", "2").convertEachOrSkipToSet("ids", "String", "Int", parseOrNull)
                survivors.toList() shouldBe listOf(3, 1, 2)
                recorder.events.map { event -> event::class.simpleName to event.path } shouldBe listOf(
                    "DroppedNullElement" to "ids[1]",
                    "DroppedBrokenElement" to "ids[2]",
                )
            }
        }

        context("convertEachOrFailToSet — OnFail.Throw to Set") {
            test("broken hard; null skips with report; convergence reported") {
                shouldThrow<MappingException.TypeConversionFailed> {
                    listOf("abc").convertEachOrFailToSet("ids", "String", "Int", parse)
                }.path shouldBe "ids[0]"

                recorder.events.clear()
                listOf("01", null, "1").convertEachOrFailToSet("ids", "String", "Int", parse) shouldBe setOf(1)
                recorder.events.map { event -> event::class.simpleName to event.path } shouldBe listOf(
                    "DroppedNullElement" to "ids[1]",
                    "ConvergedDuplicateElement" to "ids[2]",
                )
            }
        }

        context("convertEntriesOrSkip — Map default") {
            test("broken value drops the entry with the EXACT quoted entry path") {
                mapOf("a" to "1", "b" to "abc").convertEntriesOrSkip(
                    "prices",
                    convertKey = { entryKey -> entryKey },
                    convertValue = parseOrNull,
                ) shouldBe mapOf("a" to 1)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
                event.path shouldBe """prices["b"]"""
                event.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
            }
            test("key collision is last-wins + DuplicateKey with the colliding entry's path and key") {
                mapOf("x" to "1", "X" to "2").convertEntriesOrSkip(
                    "byKey",
                    convertKey = { entryKey -> entryKey.lowercase() },
                    convertValue = parseOrNull,
                ) shouldBe mapOf("x" to 2)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.DuplicateKey>()
                event.path shouldBe """byKey["X"]"""
                event.key shouldBe "X"
            }
            test("sanctioned-null key converter silently drops the entry") {
                mapOf("keep" to "1", "drop" to "2").convertEntriesOrSkip(
                    "byKey",
                    convertKey = { entryKey -> if (entryKey == "drop") null else entryKey },
                    convertValue = parseOrNull,
                ) shouldBe mapOf("keep" to 1)
                recorder.events shouldBe emptyList()
            }
            test("broken key drops the entry with a typed cause; null source value drops with report; sanctioned value drops silently") {
                val keyBoom = IllegalStateException("key boom")
                mapOf("bad" to "1", "absent" to null, "blank" to "", "ok" to "7").convertEntriesOrSkip(
                    "byKey",
                    convertKey = { entryKey -> if (entryKey == "bad") throw keyBoom else entryKey },
                    convertValue = parseOrNull,
                ) shouldBe mapOf("ok" to 7)
                recorder.events.map { event -> event::class.simpleName to event.path } shouldBe listOf(
                    "DroppedBrokenElement" to """byKey["bad"]""",
                    "DroppedNullElement" to """byKey["absent"]""",
                )
                val brokenKeyEvent = recorder.events.first().shouldBeInstanceOf<MappingDegradation.DroppedBrokenElement>()
                brokenKeyEvent.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>().cause shouldBeSameInstanceAs keyBoom
            }
        }

        context("convertEntriesOrFail — Map OnFail.Throw") {
            test("ok entries convert; broken value is hard with the quoted entry path") {
                mapOf("a" to "1").convertEntriesOrFail(
                    "prices",
                    convertKey = { entryKey -> entryKey },
                    convertValue = parse,
                ) shouldBe mapOf("a" to 1)

                val failure = shouldThrow<MappingException.TypeConversionFailed> {
                    mapOf("a" to "1", "b" to "abc").convertEntriesOrFail(
                        "prices",
                        convertKey = { entryKey -> entryKey },
                        convertValue = parse,
                    )
                }
                failure.path shouldBe """prices["b"]"""
            }
            test("broken key is hard with the quoted entry path") {
                shouldThrow<MappingException.TypeConversionFailed> {
                    mapOf("bad" to "1").convertEntriesOrFail(
                        "byKey",
                        convertKey = { entryKey: String -> throw IllegalStateException("key boom") },
                        convertValue = parse,
                    )
                }.path shouldBe """byKey["bad"]"""
            }
            test("null source value skips the entry with report; collision is last-wins + report") {
                mapOf("a" to "1", "gone" to null).convertEntriesOrFail(
                    "prices",
                    convertKey = { entryKey -> entryKey },
                    convertValue = parse,
                ) shouldBe mapOf("a" to 1)
                recorder.events.single().shouldBeInstanceOf<MappingDegradation.DroppedNullElement>()
                    .path shouldBe """prices["gone"]"""

                recorder.events.clear()
                mapOf("x" to "1", "X" to "2").convertEntriesOrFail(
                    "byKey",
                    convertKey = { entryKey -> entryKey.lowercase() },
                    convertValue = parse,
                ) shouldBe mapOf("x" to 2)
                recorder.events.single().shouldBeInstanceOf<MappingDegradation.DuplicateKey>()
            }
        }

        context("convertEntriesValueOrNull — Map with nullable target values") {
            test("broken key drops the entry (reported); broken value goes null-in-place (reported); null source value is silent null") {
                val keyBoom = IllegalStateException("key boom")
                mapOf("badKey" to "1", "badValue" to "abc", "absent" to null, "ok" to "7").convertEntriesValueOrNull(
                    "prices",
                    convertKey = { entryKey -> if (entryKey == "badKey") throw keyBoom else entryKey },
                    convertValue = parseOrNull,
                ) shouldBe mapOf("badValue" to null, "absent" to null, "ok" to 7)
                recorder.events.map { event -> event::class.simpleName to event.path } shouldBe listOf(
                    "DroppedBrokenElement" to """prices["badKey"]""",
                    "AbsorbedConversionError" to """prices["badValue"]""",
                )
                val absorbed = recorder.events[1].shouldBeInstanceOf<MappingDegradation.AbsorbedConversionError>()
                absorbed.cause.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
            }
            test("collision is reported and the value is still written (last wins)") {
                mapOf("x" to "1", "X" to "2").convertEntriesValueOrNull(
                    "byKey",
                    convertKey = { entryKey -> entryKey.lowercase() },
                    convertValue = parseOrNull,
                ) shouldBe mapOf("x" to 2)
                val event = recorder.events.single().shouldBeInstanceOf<MappingDegradation.DuplicateKey>()
                event.path shouldBe """byKey["X"]"""
                event.key shouldBe "X"
            }
        }

        context("empty input — every seam yields empty output and zero events") {
            test("lists, sets, and maps") {
                emptyList<String?>().convertEachOrSkip("xs", "String", "Int", parseOrNull) shouldBe emptyList()
                emptyList<String?>().convertEachOrNull("xs", "String", "Int", parseOrNull) shouldBe emptyList()
                emptyList<String?>().convertEachOrFail("xs", "String", "Int", parse) shouldBe emptyList()
                emptyList<String?>().convertEachOrNullStrict("xs", "String", "Int", parseOrNull) shouldBe emptyList()
                emptyList<String?>().convertEachOrSkipToSet("xs", "String", "Int", parseOrNull) shouldBe emptySet()
                emptyList<String?>().convertEachOrFailToSet("xs", "String", "Int", parse) shouldBe emptySet()
                emptyMap<String, String?>().convertEntriesOrSkip(
                    "m",
                    convertKey = { entryKey -> entryKey },
                    convertValue = parseOrNull,
                ) shouldBe emptyMap()
                emptyMap<String, String?>().convertEntriesOrFail(
                    "m",
                    convertKey = { entryKey -> entryKey },
                    convertValue = parse,
                ) shouldBe emptyMap()
                emptyMap<String, String?>().convertEntriesValueOrNull(
                    "m",
                    convertKey = { entryKey -> entryKey },
                    convertValue = parseOrNull,
                ) shouldBe emptyMap()
                recorder.events shouldBe emptyList()
            }
        }

        context("properties") {
            test("convertEachOrNull preserves SIZE for arbitrary lists with a total convert") {
                checkAll(Arb.list(Arb.int().orNull())) { numbers ->
                    numbers.convertEachOrNull("xs", "Int", "Long") { value -> value.toLong() }.size shouldBe numbers.size
                }
            }
            test("convertEachOrSkip output size equals input size minus nulls and brokens") {
                checkAll(Arb.list(Arb.int().orNull())) { numbers ->
                    val converted = numbers.convertEachOrSkip<Int, Int>("xs", "Int", "Int") { value ->
                        if (value < 0) throw IllegalArgumentException("negative") else value
                    }
                    val expectedSurvivors = numbers.count { number -> number != null && number >= 0 }
                    converted.size shouldBe expectedSurvivors
                }
            }
        }
    })
