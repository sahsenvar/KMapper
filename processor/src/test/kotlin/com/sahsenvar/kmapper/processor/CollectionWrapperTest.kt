package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class CollectionWrapperTest {

    /**
     * A @CollectionWrapper extension on List<T> → PersistentList<T> in the same compilation.
     * A @MapTo model with a PersistentList<TagDomain> target field should generate
     * tags.map { it.toTagDomain() }.asPersistentList() (or equivalent wrapper call).
     */
    @Test
    fun `List maps to PersistentList via CollectionWrapper`() {
        val src = SourceFile.kotlin(
            "W.kt",
            """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.CollectionWrapper
            import kotlinx.collections.immutable.PersistentList
            import kotlinx.collections.immutable.toPersistentList

            @CollectionWrapper(forType = PersistentList::class)
            fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()

            data class TagDomain(val name: String)
            data class ProductDomain(val tags: PersistentList<TagDomain>)

            @MapTo(TagDomain::class)
            data class TagRemote(val name: String)

            @MapTo(ProductDomain::class)
            data class ProductRemote(val tags: List<TagRemote>)
            """.trimIndent()
        )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")
        // Must have a .map call for element-level conversion
        assert(gen.contains(".map") || gen.contains("map·{")) {
            "Expected .map in generated code:\n$gen"
        }
        // Must call the wrapper function
        assert(gen.contains("asPersistentList")) {
            "Expected asPersistentList() wrapper call in generated code:\n$gen"
        }
    }

    /**
     * Two @CollectionWrapper functions targeting the same forType in the same compilation
     * must produce a COMPILATION_ERROR mentioning the conflict.
     */
    @Test
    fun `duplicate wrapper for same type fails`() {
        val src = SourceFile.kotlin(
            "Dup.kt",
            """
            import com.sahsenvar.kmapper.annotations.CollectionWrapper
            import kotlinx.collections.immutable.PersistentList
            import kotlinx.collections.immutable.toPersistentList

            @CollectionWrapper(forType = PersistentList::class)
            fun <T> List<T>.w1(): PersistentList<T> = toPersistentList()

            @CollectionWrapper(forType = PersistentList::class)
            fun <T> List<T>.w2(): PersistentList<T> = toPersistentList()
            """.trimIndent()
        )
        val (r, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(
            r.messages.contains("CollectionWrapper", ignoreCase = true) ||
                r.messages.contains("multiple", ignoreCase = true) ||
                r.messages.contains("conflict", ignoreCase = true) ||
                r.messages.contains("duplicate", ignoreCase = true)
        ) { "Expected conflict error in:\n${r.messages}" }
    }
}
