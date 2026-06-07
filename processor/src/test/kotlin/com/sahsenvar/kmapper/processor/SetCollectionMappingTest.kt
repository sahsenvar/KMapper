package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for Set<T> → Set<T> collection mapping.
 *
 * Bug: generateCollectionMapping emitted only `.map { it.toX() }`, which returns a List,
 * so assigning it to a Set<T> target did not compile.
 * Fix: when the target collection FQN is kotlin.collections.Set (or MutableSet),
 * the generator must append `.toSet()` (or `?.toSet()` for nullable source).
 */
@OptIn(ExperimentalCompilerApi::class)
class SetCollectionMappingTest {
    /**
     * Non-null Set<TagRemote> → Set<TagDomain> where TagRemote is @MapTo(TagDomain::class).
     * Generated code must:
     *   - compile (exit code OK)
     *   - contain `.map { it.toTagDomain() }.toSet()` (non-safe form, non-null source)
     *   - NOT be a bare `.map { }` without `.toSet()` (that would produce List, not Set)
     */
    @Test
    fun `non-null Set of mapped elements emits map toSet`() {
        val src =
            SourceFile.kotlin(
                "SetNested.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: Set<TagDomain>)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: Set<TagRemote>)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")

        // Must call the element mapper
        assert(gen.contains("toTagDomain()")) {
            "Expected toTagDomain() element mapper call:\n$gen"
        }
        // Must append .toSet() so the result type is Set, not List
        assert(gen.contains(".toSet()")) {
            "Expected .toSet() suffix for Set target in generated code:\n$gen"
        }
        // Must NOT use safe-call map for non-null source
        assert(!gen.contains("?.map")) {
            "Generated code must NOT use ?.map for non-null Set source:\n$gen"
        }
    }

    /**
     * Nullable Set<TagRemote>? → Set<TagDomain>?: null-safe form.
     * Generated code must use `?.map { it.toTagDomain() }?.toSet()`.
     */
    @Test
    fun `nullable Set of mapped elements emits safe-call map toSet`() {
        val src =
            SourceFile.kotlin(
                "SetNestedNullable.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: Set<TagDomain>?)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: Set<TagRemote>?)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")

        // Must call the element mapper
        assert(gen.contains("toTagDomain()")) {
            "Expected toTagDomain() element mapper call:\n$gen"
        }
        // Nullable source must use ?.map
        assert(gen.contains("?.map")) {
            "Expected ?.map for nullable Set source:\n$gen"
        }
        // Must append ?.toSet() for nullable chain
        assert(gen.contains(".toSet()")) {
            "Expected .toSet() suffix for Set target in generated code:\n$gen"
        }
    }

    /**
     * Guard: List<TagRemote> → List<TagDomain> must NOT emit .toSet().
     * Confirms the fix only applies to Set targets.
     */
    @Test
    fun `List target does NOT emit toSet`() {
        val src =
            SourceFile.kotlin(
                "ListNestedGuard.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: List<TagDomain>)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: List<TagRemote>)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")

        // Must call the element mapper
        assert(gen.contains("toTagDomain()")) {
            "Expected toTagDomain() element mapper call:\n$gen"
        }
        // Must NOT append .toSet() for List target
        assert(!gen.contains(".toSet()")) {
            "List target must NOT contain .toSet():\n$gen"
        }
    }
}
