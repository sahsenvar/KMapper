package com.sahsenvar.kmapper.validators

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class ValidatorsTest {
    // EmailValidator
    @Test fun `EmailValidator valid email`() { assertNull(EmailValidator.validate("user@example.com")) }
    @Test fun `EmailValidator missing at-sign`() { assertNotNull(EmailValidator.validate("userexample.com")) }
    @Test fun `EmailValidator missing domain`() { assertNotNull(EmailValidator.validate("user@")) }
    @Test fun `EmailValidator missing tld`() { assertNotNull(EmailValidator.validate("user@example")) }
    @Test fun `EmailValidator complex valid email`() { assertNull(EmailValidator.validate("user.name+tag@sub.example.co.uk")) }
    @Test fun `EmailValidator reason message`() {
        assertEquals("must be a valid email", EmailValidator.validate("bad"))
    }

    // UrlValidator
    @Test fun `UrlValidator valid http url`() { assertNull(UrlValidator.validate("http://example.com")) }
    @Test fun `UrlValidator valid https url`() { assertNull(UrlValidator.validate("https://www.example.com/path?q=1")) }
    @Test fun `UrlValidator missing scheme`() { assertNotNull(UrlValidator.validate("example.com")) }
    @Test fun `UrlValidator ftp scheme invalid`() { assertNotNull(UrlValidator.validate("ftp://example.com")) }
    @Test fun `UrlValidator reason message`() {
        assertEquals("must be a valid URL", UrlValidator.validate("not-a-url"))
    }
}
