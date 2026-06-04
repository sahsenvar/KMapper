package com.sahsenvar.kmapper.immutable

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlin.test.Test

class ImmutableConvertersTest {

    // ---- PersistentListWrapper ----

    @Test
    fun persistentList_preserves_order() {
        val result = PersistentListWrapper.wrap(listOf(3, 1, 2))
        (result is PersistentList) shouldBe true
        result shouldContainExactly listOf(3, 1, 2)
    }

    @Test
    fun persistentList_empty() {
        val result = PersistentListWrapper.wrap(emptyList<Int>())
        (result is PersistentList) shouldBe true
        result.size shouldBe 0
    }

    @Test
    fun persistentList_single_element() {
        val result = PersistentListWrapper.wrap(listOf(42))
        result.size shouldBe 1
        result[0] shouldBe 42
    }

    // ---- PersistentSetWrapper ----

    @Test
    fun persistentSet_dedups() {
        val result = PersistentSetWrapper.wrap(listOf(1, 2, 2, 3, 3))
        (result is PersistentSet) shouldBe true
        result.size shouldBe 3
        result.toList().sorted() shouldBe listOf(1, 2, 3)
    }

    @Test
    fun persistentSet_empty() {
        val result = PersistentSetWrapper.wrap(emptyList<Int>())
        (result is PersistentSet) shouldBe true
        result.size shouldBe 0
    }

    // ---- ImmutableListWrapper ----

    @Test
    fun immutableList_preserves_order() {
        val result = ImmutableListWrapper.wrap(listOf(10, 20, 30))
        (result is ImmutableList) shouldBe true
        result shouldContainExactly listOf(10, 20, 30)
    }

    @Test
    fun immutableList_empty() {
        val result = ImmutableListWrapper.wrap(emptyList<String>())
        (result is ImmutableList) shouldBe true
        result.size shouldBe 0
    }

    // ---- ImmutableSetWrapper ----

    @Test
    fun immutableSet_dedups() {
        val result = ImmutableSetWrapper.wrap(listOf("a", "b", "a", "c"))
        (result is ImmutableSet) shouldBe true
        result.size shouldBe 3
    }

    @Test
    fun immutableSet_empty() {
        val result = ImmutableSetWrapper.wrap(emptyList<String>())
        (result is ImmutableSet) shouldBe true
        result.size shouldBe 0
    }
}
