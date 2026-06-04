package com.sahsenvar.kmapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test

class ConvertOrFailTest {

    @Test
    fun `successful block returns its result`() {
        val result = convertOrFail("a", "b") { 1 }
        result shouldBe 1
    }

    @Test
    fun `successful block with string result`() {
        val result = convertOrFail("kotlin.Int", "kotlin.String") { 42.toString() }
        result shouldBe "42"
    }

    @Test
    fun `plain RuntimeException is wrapped as TypeConversionFailed`() {
        val ex = shouldThrow<MappingException.TypeConversionFailed> {
            convertOrFail("kotlin.String", "kotlin.Int") {
                throw RuntimeException("raw error")
            }
        }
        ex.from shouldBe "kotlin.String"
        ex.to shouldBe "kotlin.Int"
    }

    @Test
    fun `NumberFormatException is wrapped as TypeConversionFailed`() {
        val ex = shouldThrow<MappingException.TypeConversionFailed> {
            convertOrFail("kotlin.String", "kotlin.Int") {
                "abc".toInt()
            }
        }
        ex.from shouldBe "kotlin.String"
        ex.to shouldBe "kotlin.Int"
    }

    @Test
    fun `MappingException RequiredFieldMissing is re-thrown unchanged not double-wrapped`() {
        val original = MappingException.RequiredFieldMissing("id")
        val thrown = shouldThrow<MappingException.RequiredFieldMissing> {
            convertOrFail("A", "B") { throw original }
        }
        thrown shouldBeSameInstanceAs original
    }

    @Test
    fun `MappingException TypeConversionFailed is re-thrown unchanged`() {
        val original = MappingException.TypeConversionFailed("X", "Y", RuntimeException())
        val thrown = shouldThrow<MappingException.TypeConversionFailed> {
            convertOrFail("A", "B") { throw original }
        }
        thrown shouldBeSameInstanceAs original
    }

    @Test
    fun `MappingException EmptyCollection is re-thrown unchanged`() {
        val original = MappingException.EmptyCollection("detail")
        val thrown = shouldThrow<MappingException.EmptyCollection> {
            convertOrFail("A", "B") { throw original }
        }
        thrown shouldBeSameInstanceAs original
    }

    @Test
    fun `MappingException UnknownEnumValue is re-thrown unchanged`() {
        val original = MappingException.UnknownEnumValue("Status", "BAD")
        val thrown = shouldThrow<MappingException.UnknownEnumValue> {
            convertOrFail("A", "B") { throw original }
        }
        thrown shouldBeSameInstanceAs original
    }

    @Test
    fun `wrapped TypeConversionFailed preserves original cause`() {
        val cause = IllegalArgumentException("root")
        val ex = shouldThrow<MappingException.TypeConversionFailed> {
            convertOrFail("kotlin.Long", "kotlin.Int") { throw cause }
        }
        ex.cause shouldBeSameInstanceAs cause
    }
}
