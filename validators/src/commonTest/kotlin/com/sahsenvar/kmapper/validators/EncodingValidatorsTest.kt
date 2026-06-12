package com.sahsenvar.kmapper.validators

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EncodingValidatorsTest {
    // Base64Validator
    @Test fun `Base64 valid padded forms`() {
        assertNull(Base64Validator.validate("TWFu")) // "Man", no padding needed
        assertNull(Base64Validator.validate("TWE=")) // "Ma", 1 pad
        assertNull(Base64Validator.validate("TQ==")) // "M", 2 pads
    }

    @Test fun `Base64 valid unpadded forms`() {
        assertNull(Base64Validator.validate("TWE")) // remainder 3 — valid without padding
        assertNull(Base64Validator.validate("TQ")) //  remainder 2 — valid without padding
    }

    @Test fun `Base64 accepts URL-safe alphabet`() {
        assertNull(Base64Validator.validate("-_8="))
        assertNull(Base64Validator.validate("aGVsbG8td29ybGQ_")) // '?' from URL-safe '_'
    }

    @Test fun `Base64 empty string encodes zero bytes`() {
        assertNull(Base64Validator.validate(""))
    }

    @Test fun `Base64 rejects bad padding`() {
        assertNotNull(Base64Validator.validate("=")) //     padding with no body
        assertNotNull(Base64Validator.validate("T===")) //  3 pads never valid
        assertNotNull(Base64Validator.validate("TQ=")) //   2 chars need ==, not =
        assertNotNull(Base64Validator.validate("TWFu=")) // complete block + stray pad
        assertNotNull(Base64Validator.validate("TQ=X")) //  padding must be at the end
        assertNotNull(Base64Validator.validate("T=Q="))
    }

    @Test fun `Base64 rejects illegal characters and lengths`() {
        assertNotNull(Base64Validator.validate("TW!u"))
        assertNotNull(Base64Validator.validate("TWFuX")) // unpadded remainder 1 encodes no whole byte
        assertNotNull(Base64Validator.validate("açık"))
    }

    // HexStringValidator
    @Test fun `Hex valid forms`() {
        assertNull(HexStringValidator.validate("deadbeef"))
        assertNull(HexStringValidator.validate("DEADBEEF"))
        assertNull(HexStringValidator.validate("DeAd")) // mixed case
        assertNull(HexStringValidator.validate("00"))
        assertNull(HexStringValidator.validate("")) //    zero bytes
    }

    @Test fun `Hex rejects odd length and non-hex input`() {
        assertNotNull(HexStringValidator.validate("abc")) //   odd length
        assertNotNull(HexStringValidator.validate("zz"))
        assertNotNull(HexStringValidator.validate("0x12")) //  prefix not allowed
        assertNotNull(HexStringValidator.validate("12 34")) // no separators
    }
}
