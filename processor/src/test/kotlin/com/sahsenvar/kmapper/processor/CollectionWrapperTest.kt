@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class CollectionWrapperTest {
    /**
     * A @CollectionWrapper object in the same compilation, listed in @KMapperConfig.wrappers.
     * A @MapTo model with a PersistentList<TagDomain> target field should generate
     * W.wrap(tags.map { it.toTagDomain() }) — the wrapper object's wrap() call.
     */
    @Test
    fun `List maps to PersistentList via CollectionWrapper object`() {
        val src =
            SourceFile.kotlin(
                "W.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.CollectionWrapper
                import com.sahsenvar.kmapper.annotations.KMapperConfig
                import kotlinx.collections.immutable.PersistentList
                import kotlinx.collections.immutable.toPersistentList

                @CollectionWrapper(forType = PersistentList::class)
                object W {
                    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
                }

                @KMapperConfig(wrappers = [W::class])
                object Cfg

                data class TagDomain(val name: String)
                data class ProductDomain(val tags: PersistentList<TagDomain>)

                @MapTo(TagDomain::class)
                data class TagRemote(val name: String)

                @MapTo(ProductDomain::class)
                data class ProductRemote(val tags: List<TagRemote>)
                """.trimIndent(),
            )
        val (r, compilation) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ProductRemoteMappers.kt")
        // Must have a .map call for element-level conversion
        assertTrue(gen.contains(".map") || gen.contains("map·{"), "Expected .map in generated code:\n$gen")
        // Must call the wrapper object's wrap method
        assertTrue(gen.contains("W.wrap("), "Expected W.wrap( call in generated code:\n$gen")
    }

    /**
     * Runtime-exec: compile, classload, invoke — verify the wrapped collection has the correct elements.
     */
    @Test
    fun `runtime exec — wrapped collection has correct elements`() {
        val src =
            SourceFile.kotlin(
                "RT.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.annotations.CollectionWrapper
                import com.sahsenvar.kmapper.annotations.KMapperConfig
                import kotlinx.collections.immutable.PersistentList
                import kotlinx.collections.immutable.toPersistentList

                @CollectionWrapper(forType = PersistentList::class)
                object RTWrapper {
                    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
                }

                @KMapperConfig(wrappers = [RTWrapper::class])
                object RTCfg

                data class ItemDomain(val value: Int)

                data class ContainerDomain(val items: PersistentList<ItemDomain>)

                @MapTo(ItemDomain::class)
                data class ItemRemote(val value: Int)

                @MapTo(ContainerDomain::class)
                data class ContainerRemote(val items: List<ItemRemote>)
                """.trimIndent(),
            )
        val (r, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)

        // Invoke via reflection
        val item1 = r.newInstance("ItemRemote", 10)
        val item2 = r.newInstance("ItemRemote", 20)
        val container = r.newInstance("ContainerRemote", listOf(item1, item2))

        val result = r.invokeMapper("ContainerRemoteMappersKt", "toContainerDomain", container)
        assertNotNull(result)
        val items = result.prop("items")
        assertNotNull(items)
        val itemsList = (items as Iterable<*>).toList()
        assertEquals(2, itemsList.size)
        assertEquals(10, itemsList[0]?.prop("value"))
        assertEquals(20, itemsList[1]?.prop("value"))
    }

    /**
     * Two wrapper objects targeting the same forType, both listed in @KMapperConfig.wrappers,
     * must produce a COMPILATION_ERROR mentioning the conflict.
     */
    @Test
    fun `duplicate wrapper for same type fails`() {
        val src =
            SourceFile.kotlin(
                "Dup.kt",
                """
                import com.sahsenvar.kmapper.annotations.CollectionWrapper
                import com.sahsenvar.kmapper.annotations.KMapperConfig
                import kotlinx.collections.immutable.PersistentList
                import kotlinx.collections.immutable.toPersistentList

                @CollectionWrapper(forType = PersistentList::class)
                object W1 {
                    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
                }

                @CollectionWrapper(forType = PersistentList::class)
                object W2 {
                    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
                }

                @KMapperConfig(wrappers = [W1::class, W2::class])
                object DupCfg
                """.trimIndent(),
            )
        val (r, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assertTrue(
            r.messages.contains("CollectionWrapper", ignoreCase = true) ||
                r.messages.contains("Duplicate", ignoreCase = true) ||
                r.messages.contains("duplicate", ignoreCase = true) ||
                r.messages.contains("conflict", ignoreCase = true) ||
                r.messages.contains("multiple", ignoreCase = true),
            "Expected conflict error in:\n${r.messages}",
        )
    }
}
