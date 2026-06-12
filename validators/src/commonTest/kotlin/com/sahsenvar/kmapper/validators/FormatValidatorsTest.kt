package com.sahsenvar.kmapper.validators

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FormatValidatorsTest {
    // PhoneE164Validator
    @Test fun `PhoneE164 valid numbers`() {
        assertNull(PhoneE164Validator.validate("+905551112233"))
        assertNull(PhoneE164Validator.validate("+14155552671"))
        assertNull(PhoneE164Validator.validate("+12")) //               shortest: country digit + 1
        assertNull(PhoneE164Validator.validate("+123456789012345")) //  longest: 15 digits total
    }

    @Test fun `PhoneE164 rejects malformed numbers`() {
        assertNotNull(PhoneE164Validator.validate("905551112233")) //     missing +
        assertNotNull(PhoneE164Validator.validate("+0123456789")) //      country code cannot start with 0
        assertNotNull(PhoneE164Validator.validate("+1234567890123456")) // 16 digits — too long
        assertNotNull(PhoneE164Validator.validate("+1")) //               + and a single digit — too short
        assertNotNull(PhoneE164Validator.validate("+90 555 111 22 33")) // spaces are display format, not E.164
        assertNotNull(PhoneE164Validator.validate("+90-555-1112233"))
        assertNotNull(PhoneE164Validator.validate("+9055511122a3"))
        assertNotNull(PhoneE164Validator.validate(""))
        assertNotNull(PhoneE164Validator.validate("+"))
    }

    @Test fun `PhoneE164 reason message`() {
        assertEquals("must be an E.164 phone number like +905551112233", PhoneE164Validator.validate("bad"))
    }

    // UuidStringValidator
    @Test fun `UuidString valid canonical forms`() {
        assertNull(UuidStringValidator.validate("123e4567-e89b-12d3-a456-426614174000"))
        assertNull(UuidStringValidator.validate("123E4567-E89B-12D3-A456-426614174000")) // upper-case
        assertNull(UuidStringValidator.validate("00000000-0000-0000-0000-000000000000")) // nil UUID
        assertNull(UuidStringValidator.validate("FFFFFFFF-ffff-FFFF-ffff-FFFFFFFFffff")) // mixed case
    }

    @Test fun `UuidString rejects non-canonical forms`() {
        assertNotNull(UuidStringValidator.validate("123e4567e89b12d3a456426614174000")) //    compact (no dashes)
        assertNotNull(UuidStringValidator.validate("{123e4567-e89b-12d3-a456-426614174000}")) // braced
        assertNotNull(UuidStringValidator.validate("123e4567-e89b-12d3-a456-42661417400")) //  last group too short
        assertNotNull(UuidStringValidator.validate("123e4567-e89b-12d3-a456-4266141740000")) // last group too long
        assertNotNull(UuidStringValidator.validate("123g4567-e89b-12d3-a456-426614174000")) // non-hex digit
        assertNotNull(UuidStringValidator.validate(" 123e4567-e89b-12d3-a456-426614174000"))
        assertNotNull(UuidStringValidator.validate(""))
    }

    // SlugValidator
    @Test fun `Slug valid forms`() {
        assertNull(SlugValidator.validate("a")) //                     single char
        assertNull(SlugValidator.validate("converter-redesign-2"))
        assertNull(SlugValidator.validate("2026"))
    }

    @Test fun `Slug rejects malformed forms`() {
        assertNotNull(SlugValidator.validate("")) //          empty
        assertNotNull(SlugValidator.validate("Upper-Case")) // upper-case
        assertNotNull(SlugValidator.validate("-leading"))
        assertNotNull(SlugValidator.validate("trailing-"))
        assertNotNull(SlugValidator.validate("double--hyphen"))
        assertNotNull(SlugValidator.validate("with space"))
        assertNotNull(SlugValidator.validate("under_score"))
        assertNotNull(SlugValidator.validate("türkçe")) //    non-ASCII
    }

    // HostnameValidator
    @Test fun `Hostname valid forms`() {
        assertNull(HostnameValidator.validate("localhost")) //           single label
        assertNull(HostnameValidator.validate("example.com"))
        assertNull(HostnameValidator.validate("sub-domain.example.co.uk"))
        assertNull(HostnameValidator.validate("xn--bcher-kva.example")) // punycode
        assertNull(HostnameValidator.validate("9to5.example")) //        digit-leading label (RFC 1123)
        assertNull(HostnameValidator.validate("a".repeat(63) + ".example")) // max label length
    }

    @Test fun `Hostname rejects malformed forms`() {
        assertNotNull(HostnameValidator.validate(""))
        assertNotNull(HostnameValidator.validate("a".repeat(64) + ".example")) // label too long
        assertNotNull(HostnameValidator.validate(("a.".repeat(127)) + "toolong")) // > 253 chars total
        assertNotNull(HostnameValidator.validate("-bad.example")) //  leading hyphen in label
        assertNotNull(HostnameValidator.validate("bad-.example")) //  trailing hyphen in label
        assertNotNull(HostnameValidator.validate("exa mple.com"))
        assertNotNull(HostnameValidator.validate("example..com")) // empty label
        assertNotNull(HostnameValidator.validate("example.com.")) // trailing dot
        assertNotNull(HostnameValidator.validate("under_score.example"))
    }
}
