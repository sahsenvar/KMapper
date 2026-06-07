package com.sahsenvar.kmapper.uri

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NsUrlConverterTest {
    // Use pre-normalized URLs: NSURL adds trailing slash to bare hosts.
    private val normalizedUrl = "https://example.com/"

    @Test fun `NsUrlStringConverter round-trip normalized URL`() {
        val nsUrl = NsUrlStringConverter.convertToNonNull(normalizedUrl)
        NsUrlStringConverter.convertFromNonNull(nsUrl) shouldBe normalizedUrl
    }

    @Test fun `NsUrlStringConverter round-trip path URL`() {
        val pathUrl = "https://api.example.com/v1/resource"
        val nsUrl = NsUrlStringConverter.convertToNonNull(pathUrl)
        NsUrlStringConverter.convertFromNonNull(nsUrl) shouldBe pathUrl
    }
}
