package com.sahsenvar.kmapper.immutable

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlin.test.Test

class ImmutableConvertersTest {

    // ---- asPersistentList ----

    @Test
    fun asPersistentList_preserves_order() {
        val result = listOf(3, 1, 2).asPersistentList()
        (result is PersistentList) shouldBe true
        result shouldContainExactly listOf(3, 1, 2)
    }

    @Test
    fun asPersistentList_empty() {
        val result = emptyList<Int>().asPersistentList()
        (result is PersistentList) shouldBe true
        result.size shouldBe 0
    }

    @Test
    fun asPersistentList_single_element() {
        val result = listOf(42).asPersistentList()
        result.size shouldBe 1
        result[0] shouldBe 42
    }

    // ---- asPersistentSet ----

    @Test
    fun asPersistentSet_dedups() {
        val result = listOf(1, 2, 2, 3, 3).asPersistentSet()
        (result is PersistentSet) shouldBe true
        result.size shouldBe 3
        result.toList().sorted() shouldBe listOf(1, 2, 3)
    }

    @Test
    fun asPersistentSet_empty() {
        val result = emptyList<Int>().asPersistentSet()
        (result is PersistentSet) shouldBe true
        result.size shouldBe 0
    }

    // ---- asImmutableList ----

    @Test
    fun asImmutableList_preserves_order() {
        val result = listOf(10, 20, 30).asImmutableList()
        (result is ImmutableList) shouldBe true
        result shouldContainExactly listOf(10, 20, 30)
    }

    @Test
    fun asImmutableList_empty() {
        val result = emptyList<String>().asImmutableList()
        (result is ImmutableList) shouldBe true
        result.size shouldBe 0
    }

    // ---- asImmutableSet ----

    @Test
    fun asImmutableSet_dedups() {
        val result = listOf("a", "b", "a", "c").asImmutableSet()
        (result is ImmutableSet) shouldBe true
        result.size shouldBe 3
    }

    @Test
    fun asImmutableSet_empty() {
        val result = emptyList<String>().asImmutableSet()
        (result is ImmutableSet) shouldBe true
        result.size shouldBe 0
    }
}
