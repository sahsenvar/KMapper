package com.sahsenvar.kmapper.okio

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertTrue

class OkioConverterTest {
    // StringByteStringConverter

    @Test fun `StringByteStringConverter round-trip non-empty`() {
        val original = "hello KMapper"
        StringByteStringConverter.convertFrom(
            StringByteStringConverter.convertTo(original),
        ) shouldBe original
    }

    @Test fun `StringByteStringConverter round-trip empty string`() {
        val original = ""
        StringByteStringConverter.convertFrom(
            StringByteStringConverter.convertTo(original),
        ) shouldBe original
    }

    // ByteArrayByteStringConverter — compare contents, NOT reference

    @Test fun `ByteArrayByteStringConverter round-trip`() {
        val original = byteArrayOf(1, 2, 3, 4, 127, -1)
        val roundTripped =
            ByteArrayByteStringConverter.convertFrom(
                ByteArrayByteStringConverter.convertTo(original),
            )
        assertTrue(roundTripped.contentEquals(original), "ByteArray contents must be equal")
    }

    @Test fun `ByteArrayByteStringConverter round-trip empty`() {
        val original = byteArrayOf()
        val roundTripped =
            ByteArrayByteStringConverter.convertFrom(
                ByteArrayByteStringConverter.convertTo(original),
            )
        assertTrue(roundTripped.contentEquals(original), "Empty ByteArray round-trip")
    }

    // StringPathConverter

    @Test fun `StringPathConverter round-trip unix path`() {
        val original = "/tmp/test"
        StringPathConverter.convertFrom(
            StringPathConverter.convertTo(original),
        ) shouldBe original
    }
}
