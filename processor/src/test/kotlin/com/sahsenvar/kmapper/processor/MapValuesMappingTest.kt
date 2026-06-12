@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compile + runtime tests for Map<K,V> value mapping (Task 6, spec §4).
 *
 * Coverage:
 *  1. Map<String, ValueR> → Map<String, ValueD> (nested value) — codegen + runtime
 *  2. Map<String, String> → Map<String, String> (direct passthrough) — codegen + runtime
 *  3. Nullable Map<String, ValueR>? → required target — nullable source wrap + present-map maps
 *  4. Key mismatch Map<Int, X> → Map<String, X> → Unmappable (no mapValues emitted)
 *  5. Regression: existing List/collection mapping still passes
 */
class MapValuesMappingTest {
    // ─── shared model sources ──────────────────────────────────────────────────

    private val nestedValueSrc =
        SourceFile.kotlin(
            "MapNested.kt",
            """
            import com.sahsenvar.kmapper.annotations.MapTo

            data class ValueD(val label: String)

            @MapTo(ValueD::class)
            data class ValueR(val label: String)

            data class ContainerD(val items: Map<String, ValueD>)

            @MapTo(ContainerD::class)
            data class ContainerR(val items: Map<String, ValueR>)
            """.trimIndent(),
        )

    private val directValueSrc =
        SourceFile.kotlin(
            "MapDirect.kt",
            """
            import com.sahsenvar.kmapper.annotations.MapTo

            data class BagD(val tags: Map<String, String>)

            @MapTo(BagD::class)
            data class BagR(val tags: Map<String, String>)
            """.trimIndent(),
        )

    private val nullableValueSrc =
        SourceFile.kotlin(
            "MapNullable.kt",
            """
            import com.sahsenvar.kmapper.annotations.MapTo

            data class ValD(val n: Int)

            @MapTo(ValD::class)
            data class ValR(val n: Int)

            data class BoxD(val m: Map<String, ValD>)

            @MapTo(BoxD::class)
            data class BoxR(val m: Map<String, ValR>?)
            """.trimIndent(),
        )

    // ─── Test 1: Map<String, ValueR> → Map<String, ValueD> nested mapping ─────

    @Test
    fun `map with nested value type emits mapValues lambda in generated code`() {
        val (r, compilation) = compile(nestedValueSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ContainerRMappers.kt")
        // Must ride the per-entry seam with real key/value type-pair literals
        assert(gen.contains("convertEntriesOrSkip(\"items\", \"kotlin.String\", \"kotlin.String\", \"ValueR\", \"ValueD\"")) {
            "Expected convertEntriesOrSkip with key/value FQN pairs in generated code:\n$gen"
        }
        // Must call the value mapper through the Result boundary
        assert(gen.contains("toValueDResult().getOrThrow()")) {
            "Expected toValueDResult().getOrThrow() call inside the entry seam:\n$gen"
        }
    }

    @Test
    fun `map with nested value type maps both entries at runtime`() {
        val (result, _) = compile(nestedValueSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // Build Map<String, ValueR> = {"a" → ValueR("hello"), "b" → ValueR("world")}
        val valueRClass = result.classLoader.loadClass("ValueR")
        val valA =
            valueRClass.declaredConstructors
                .first { it.parameterCount == 1 }
                .newInstance("hello")
        val valB =
            valueRClass.declaredConstructors
                .first { it.parameterCount == 1 }
                .newInstance("world")
        val inputMap = mapOf("a" to valA, "b" to valB)

        val containerRClass = result.classLoader.loadClass("ContainerR")
        val instance =
            containerRClass.declaredConstructors
                .first { it.parameterCount == 1 }
                .newInstance(inputMap)

        val domain = result.invokeResultMapper("ContainerRMappersKt", "toContainerDResult", instance).getOrThrow()!!

        @Suppress("UNCHECKED_CAST")
        val resultMap = domain.prop("items") as Map<String, Any>
        assertEquals(2, resultMap.size)
        assertEquals("hello", resultMap["a"]!!.prop("label"))
        assertEquals("world", resultMap["b"]!!.prop("label"))
    }

    // ─── Test 2: Map<String, String> → Map<String, String> direct passthrough ─

    @Test
    fun `map with same-type values generates passthrough (source reference) not mapValues`() {
        val (r, compilation) = compile(directValueSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("BagRMappers.kt")
        // Direct passthrough: should contain the field name "tags" but NO entry seam
        assert(!gen.contains("convertEntries")) {
            "Direct Map<String,String> should NOT emit an entry seam, but got:\n$gen"
        }
        assert(gen.contains("tags")) {
            "Expected field name 'tags' in generated code:\n$gen"
        }
    }

    @Test
    fun `map with same-type values passes map through at runtime without modification`() {
        val (result, _) = compile(directValueSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val inputMap = mapOf("x" to "foo", "y" to "bar")
        val instance = result.newInstance("BagR", inputMap)

        val domain = result.invokeResultMapper("BagRMappersKt", "toBagDResult", instance).getOrThrow()!!

        @Suppress("UNCHECKED_CAST")
        val resultMap = domain.prop("tags") as Map<String, String>
        assertEquals(mapOf("x" to "foo", "y" to "bar"), resultMap)
    }

    // ─── Test 3: nullable source Map<String, ValR>? → required target ──────────

    @Test
    fun `nullable map source emits safe-call mapValues in generated code`() {
        val (r, compilation) = compile(nullableValueSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("BoxRMappers.kt")
        // Nullable source → safe-called entry seam, landed by the container ladder (orRequired)
        assert(gen.contains("m?.convertEntriesOrSkip(")) {
            "Expected m?.convertEntriesOrSkip( for nullable Map source:\n$gen"
        }
        assert(gen.contains(".orRequired(\"m\")")) {
            "Expected the container-ladder orRequired landing for the hard target:\n$gen"
        }
    }

    @Test
    fun `nullable map source null fails with RequiredFieldMissing at the Result boundary`() {
        val (result, _) = compile(nullableValueSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val instance = result.newInstance("BoxR", null as Any?)

        val outcome = result.invokeResultMapper("BoxRMappersKt", "toBoxDResult", instance)
        assert(outcome.isFailure) { "Expected Result.failure for absent required map" }
        val ex = outcome.exceptionOrNull()!!
        assert(ex.javaClass.name.contains("RequiredFieldMissing")) {
            "Expected RequiredFieldMissing but got: ${ex.javaClass.name}: ${ex.message}"
        }
    }

    @Test
    fun `nullable map source with present map maps correctly at runtime`() {
        val (result, _) = compile(nullableValueSrc)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val valRClass = result.classLoader.loadClass("ValR")
        val v1 = valRClass.declaredConstructors.first { it.parameterCount == 1 }.newInstance(99)
        val inputMap = mapOf("k" to v1)

        val instance = result.newInstance("BoxR", inputMap as Any?)

        val domain = result.invokeResultMapper("BoxRMappersKt", "toBoxDResult", instance).getOrThrow()!!

        @Suppress("UNCHECKED_CAST")
        val resultMap = domain.prop("m") as Map<String, Any>
        assertEquals(1, resultMap.size)
        assertEquals(99, resultMap["k"]!!.prop("n"))
    }

    // ─── Test 4: key mismatch → Unmappable ─────────────────────────────────────

    @Test
    fun `key type mismatch emits compiler error not mapValues`() {
        val src =
            SourceFile.kotlin(
                "MapKeyMismatch.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class ZD(val score: Int)

                @MapTo(ZD::class)
                data class ZR(val score: Int)

                data class TableD(val data: Map<String, ZD>)

                @MapTo(TableD::class)
                data class TableR(val data: Map<Int, ZR>)
                """.trimIndent(),
            )
        val (r, _) = compile(src)
        // Key mismatch → Unmappable → processor emits a compile error
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode, r.messages)
        // Error should mention the mismatch or converter
        assert(
            r.messages.contains("no converter", ignoreCase = true) ||
                r.messages.contains("unmappable", ignoreCase = true) ||
                r.messages.contains("converter", ignoreCase = true),
        ) { "Expected converter/unmappable error but got:\n${r.messages}" }
    }

    // ─── Test 5: regression — existing list collection mapping still works ──────

    @Test
    fun `list collection mapping still works after map support added`() {
        val src =
            SourceFile.kotlin(
                "ListRegression.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class ItemD(val id: String)

                @MapTo(ItemD::class)
                data class ItemR(val id: String)

                data class OrderD(val items: List<ItemD>)

                @MapTo(OrderD::class)
                data class OrderR(val items: List<ItemR>)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("OrderRMappers.kt")
        assert(gen.contains("convertEachOrSkip(\"items\"")) {
            "Expected convertEachOrSkip in list mapping:\n$gen"
        }
        assert(gen.contains("toItemDResult().getOrThrow()")) {
            "Expected toItemDResult().getOrThrow() in list mapping:\n$gen"
        }
    }
}
