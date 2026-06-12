package com.sahsenvar.kmapper.okio

import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertFailsWith
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

    // Base64ByteStringConverter

    @Test fun `Base64ByteStringConverter decodes a known RFC 4648 vector`() {
        Base64ByteStringConverter.convertTo("TWFu") shouldBe "Man".encodeUtf8()
    }

    @Test fun `Base64ByteStringConverter encodes with the standard alphabet and padding`() {
        Base64ByteStringConverter.convertFrom("Man".encodeUtf8()) shouldBe "TWFu"
        Base64ByteStringConverter.convertFrom("Ma".encodeUtf8()) shouldBe "TWE="
    }

    @Test fun `Base64ByteStringConverter round-trip including empty`() {
        val original = "hello KMapper".encodeUtf8()
        Base64ByteStringConverter.convertTo(Base64ByteStringConverter.convertFrom(original)) shouldBe original
        Base64ByteStringConverter.convertTo("") shouldBe "".encodeUtf8()
    }

    @Test fun `Base64ByteStringConverter rejects malformed input`() {
        assertFailsWith<IllegalArgumentException> { Base64ByteStringConverter.convertTo("!!! not base64 !!!") }
    }

    // Base64UrlByteStringConverter

    @Test fun `Base64UrlByteStringConverter emits URL-safe alphabet but accepts both on decode`() {
        val bytes = byteArrayOf(-5, -1) // 0xFB 0xFF → '+/' in standard, '-_' in URL-safe
        val byteString = ByteArrayByteStringConverter.convertTo(bytes)
        Base64UrlByteStringConverter.convertFrom(byteString) shouldBe "-_8="
        Base64UrlByteStringConverter.convertTo("-_8=") shouldBe byteString
        Base64UrlByteStringConverter.convertTo("+/8=") shouldBe byteString
    }

    // HexByteStringConverter

    @Test fun `HexByteStringConverter decodes both cases and encodes lower-case`() {
        val expected = byteArrayOf(-34, -83, -66, -17) // 0xDE 0xAD 0xBE 0xEF
        val byteString = ByteArrayByteStringConverter.convertTo(expected)
        HexByteStringConverter.convertTo("deadbeef") shouldBe byteString
        HexByteStringConverter.convertTo("DEADBEEF") shouldBe byteString
        HexByteStringConverter.convertFrom(byteString) shouldBe "deadbeef"
    }

    @Test fun `HexByteStringConverter round-trip including empty`() {
        HexByteStringConverter.convertTo(HexByteStringConverter.convertFrom("KMapper".encodeUtf8())) shouldBe
            "KMapper".encodeUtf8()
        HexByteStringConverter.convertTo("") shouldBe "".encodeUtf8()
    }

    @Test fun `HexByteStringConverter rejects odd-length and non-hex input`() {
        assertFailsWith<IllegalArgumentException> { HexByteStringConverter.convertTo("abc") }
        assertFailsWith<IllegalArgumentException> { HexByteStringConverter.convertTo("zz") }
    }
}
