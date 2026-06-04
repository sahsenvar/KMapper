package com.sahsenvar.kmapper

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class MappingExceptionTest {

    @Test
    fun `RequiredFieldMissing message contains field name`() {
        val ex = MappingException.RequiredFieldMissing("userId")
        ex.field shouldBe "userId"
        ex.message shouldContain "userId"
    }

    @Test
    fun `RequiredFieldMissing is a MappingException`() {
        val ex = MappingException.RequiredFieldMissing("email")
        (ex is MappingException) shouldBe true
        (ex is RuntimeException) shouldBe true
    }

    @Test
    fun `TypeConversionFailed stores from and to`() {
        val cause = RuntimeException("bad")
        val ex = MappingException.TypeConversionFailed("kotlin.String", "kotlin.Int", cause)
        ex.from shouldBe "kotlin.String"
        ex.to shouldBe "kotlin.Int"
        ex.cause shouldBe cause
        ex.message shouldContain "kotlin.String"
        ex.message shouldContain "kotlin.Int"
    }

    @Test
    fun `TypeConversionFailed is a MappingException`() {
        val ex = MappingException.TypeConversionFailed("A", "B", RuntimeException())
        (ex is MappingException) shouldBe true
    }

    @Test
    fun `UnknownEnumValue stores enum name and raw value`() {
        val ex = MappingException.UnknownEnumValue("Status", "INVALID")
        ex.enum shouldBe "Status"
        ex.value shouldBe "INVALID"
        ex.message shouldContain "Status"
        ex.message shouldContain "INVALID"
    }

    @Test
    fun `UnknownEnumValue is a MappingException`() {
        val ex = MappingException.UnknownEnumValue("Color", 99)
        (ex is MappingException) shouldBe true
    }

    @Test
    fun `EmptyCollection stores detail`() {
        val ex = MappingException.EmptyCollection("tags must have at least one entry")
        ex.detail shouldBe "tags must have at least one entry"
        ex.message shouldContain "tags must have at least one entry"
    }

    @Test
    fun `EmptyCollection is a MappingException`() {
        val ex = MappingException.EmptyCollection("detail")
        (ex is MappingException) shouldBe true
    }
}
