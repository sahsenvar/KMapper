package com.sahsenvar.kmapper.uri

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class JavaUriConverterTest {
    @Test fun `JavaStringUriConverter round-trip https`() {
        val original = "https://example.com/"
        JavaStringUriConverter.convertFromNonNull(
            JavaStringUriConverter.convertToNonNull(original),
        ) shouldBe original
    }

    @Test fun `JavaStringUriConverter round-trip ftp`() {
        val original = "ftp://files.example.org/pub"
        JavaStringUriConverter.convertFromNonNull(
            JavaStringUriConverter.convertToNonNull(original),
        ) shouldBe original
    }
}
