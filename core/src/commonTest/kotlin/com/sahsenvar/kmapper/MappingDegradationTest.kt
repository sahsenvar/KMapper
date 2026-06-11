package com.sahsenvar.kmapper

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * Pins the sealed contract: an exhaustive `when` over [MappingDegradation] must compile
 * WITHOUT an else branch. Adding a new event type breaks this function at compile time,
 * forcing every observer to handle it consciously.
 */
private fun describeDegradation(event: MappingDegradation): String = when (event) {
    is MappingDegradation.AbsorbedConversionError -> "absorbed:${event.path}:${event.from}->${event.to}"
    is MappingDegradation.DroppedBrokenElement -> "dropped-broken:${event.path}"
    is MappingDegradation.DroppedNullElement -> "dropped-null:${event.path}"
    is MappingDegradation.DuplicateKey -> "duplicate-key:${event.path}:${event.key}"
    is MappingDegradation.ConvergedDuplicateElement -> "converged:${event.path}"
}

/** Listener that records both lifecycle and degradation events through one registration. */
private class DualRecordingListener : MappingListener {
    val degradations = mutableListOf<MappingDegradation>()
    val startedSources = mutableListOf<Any>()

    override fun onDegradation(event: MappingDegradation) {
        degradations.add(event)
    }

    override fun onMapStart(
        source: Any,
        target: kotlin.reflect.KClass<*>,
    ) {
        startedSources.add(source)
    }
}

class MappingDegradationTest :
    FunSpec({

        // KMapper's listener registry is global mutable state. Every listener added during a
        // test is tracked here and removed in afterTest, so a failing assertion mid-test can
        // never leak a listener into other tests.
        val trackedListeners = mutableListOf<MappingListener>()

        fun registerTracked(listener: MappingListener) {
            trackedListeners.add(listener)
            KMapper.addListener(listener)
        }

        afterTest {
            trackedListeners.forEach(KMapper::removeListener)
            trackedListeners.clear()
        }

        // ─── Event payloads ─────────────────────────────────────────────────────

        context("event payloads") {
            test("AbsorbedConversionError exposes path, from, to and cause") {
                val cause = NumberFormatException("not a number: 'abc'")
                val event =
                    MappingDegradation.AbsorbedConversionError(
                        path = "user.age",
                        from = "String",
                        to = "Int",
                        cause = cause,
                    )

                val baseTyped: MappingDegradation = event
                baseTyped.path shouldBe "user.age"
                event.from shouldBe "String"
                event.to shouldBe "Int"
                event.cause shouldBeSameInstanceAs cause
            }

            test("DroppedBrokenElement exposes indexed path and cause") {
                val cause = IllegalArgumentException("malformed element")
                val event = MappingDegradation.DroppedBrokenElement(path = "items[3]", cause = cause)

                val baseTyped: MappingDegradation = event
                baseTyped.path shouldBe "items[3]"
                event.cause shouldBeSameInstanceAs cause
            }

            test("DroppedNullElement exposes indexed path") {
                val event = MappingDegradation.DroppedNullElement(path = "tags[0]")

                val baseTyped: MappingDegradation = event
                baseTyped.path shouldBe "tags[0]"
            }

            test("DuplicateKey exposes quoted-key path and the converged key") {
                val event = MappingDegradation.DuplicateKey(path = "prices[\"usd\"]", key = "usd")

                val baseTyped: MappingDegradation = event
                baseTyped.path shouldBe "prices[\"usd\"]"
                event.key shouldBe "usd"
            }

            test("ConvergedDuplicateElement exposes path") {
                val event = MappingDegradation.ConvergedDuplicateElement(path = "roles")

                val baseTyped: MappingDegradation = event
                baseTyped.path shouldBe "roles"
            }

            test("empty path is carried verbatim (root-level degradation)") {
                val event = MappingDegradation.DroppedNullElement(path = "")
                event.path shouldBe ""
            }
        }

        // ─── Dispatch through the listener registry ─────────────────────────────

        context("dispatch") {
            test("a registered recording listener receives the dispatched event") {
                val recorder = RecordingDegradationListener()
                registerTracked(recorder)
                val event = MappingDegradation.DroppedNullElement(path = "items[1]")

                KMapper.dispatch { onDegradation(event) }

                recorder.events.size shouldBe 1
                recorder.events.single() shouldBeSameInstanceAs event
            }

            test("a listener that does not override onDegradation is unaffected (default no-op)") {
                val plainListener = object : MappingListener {}
                val recorder = RecordingDegradationListener()
                registerTracked(plainListener)
                registerTracked(recorder)
                val event = MappingDegradation.DuplicateKey(path = "prices[\"eur\"]", key = "eur")

                KMapper.dispatch { onDegradation(event) }

                recorder.events.size shouldBe 1
                recorder.events.single() shouldBeSameInstanceAs event
            }

            test("two recording listeners both receive the same event instance") {
                val firstRecorder = RecordingDegradationListener()
                val secondRecorder = RecordingDegradationListener()
                registerTracked(firstRecorder)
                registerTracked(secondRecorder)
                val event =
                    MappingDegradation.AbsorbedConversionError(
                        path = "order.total",
                        from = "String",
                        to = "Long",
                        cause = NumberFormatException("overflow"),
                    )

                KMapper.dispatch { onDegradation(event) }

                firstRecorder.events.single() shouldBeSameInstanceAs event
                secondRecorder.events.single() shouldBeSameInstanceAs event
                firstRecorder.events.single() shouldBeSameInstanceAs secondRecorder.events.single()
            }

            test("a removed listener receives nothing further") {
                val recorder = RecordingDegradationListener()
                registerTracked(recorder)
                val firstEvent = MappingDegradation.DroppedBrokenElement(path = "items[0]", cause = RuntimeException("broken"))
                KMapper.dispatch { onDegradation(firstEvent) }

                KMapper.removeListener(recorder)
                val secondEvent = MappingDegradation.DroppedBrokenElement(path = "items[1]", cause = RuntimeException("also broken"))
                KMapper.dispatch { onDegradation(secondEvent) }

                recorder.events.size shouldBe 1
                recorder.events.single() shouldBeSameInstanceAs firstEvent
            }

            test("hasListeners flips false after removing all, so the guarded-dispatch idiom skips entirely") {
                val recorder = RecordingDegradationListener()
                registerTracked(recorder)
                KMapper.removeListener(recorder)

                KMapper.hasListeners shouldBe false

                // Generated mappers guard dispatch exactly like this — with no listeners the
                // event is never even constructed into a dispatch call.
                val event = MappingDegradation.ConvergedDuplicateElement(path = "roles")
                if (KMapper.hasListeners) {
                    KMapper.dispatch { onDegradation(event) }
                }

                recorder.events.shouldBeEmpty()
            }

            test("onDegradation coexists with existing lifecycle methods through one registration") {
                val dualListener = DualRecordingListener()
                registerTracked(dualListener)
                val source = "wire-payload"
                val event = MappingDegradation.DroppedNullElement(path = "items[2]")

                KMapper.dispatch { onMapStart(source, String::class) }
                KMapper.dispatch { onDegradation(event) }

                dualListener.startedSources.single() shouldBe source
                dualListener.degradations.single() shouldBeSameInstanceAs event
            }
        }

        // ─── Sealed-family contract ─────────────────────────────────────────────

        context("sealed-family sanity") {
            test("exhaustive when without else covers every event type") {
                val cause = RuntimeException("boom")
                describeDegradation(
                    MappingDegradation.AbsorbedConversionError(path = "a", from = "String", to = "Int", cause = cause),
                ) shouldBe "absorbed:a:String->Int"
                describeDegradation(MappingDegradation.DroppedBrokenElement(path = "b[1]", cause = cause)) shouldBe
                    "dropped-broken:b[1]"
                describeDegradation(MappingDegradation.DroppedNullElement(path = "c[2]")) shouldBe "dropped-null:c[2]"
                describeDegradation(MappingDegradation.DuplicateKey(path = "d[\"k\"]", key = "k")) shouldBe
                    "duplicate-key:d[\"k\"]:k"
                describeDegradation(MappingDegradation.ConvergedDuplicateElement(path = "e")) shouldBe "converged:e"
            }
        }
    })
