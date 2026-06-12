package com.sahsenvar.kmapper.validators

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GeoAndPaymentValidatorsTest {
    // LatitudeValidator / LongitudeValidator
    @Test fun `Latitude boundaries inclusive`() {
        assertNull(LatitudeValidator.validate(-90.0))
        assertNull(LatitudeValidator.validate(90.0))
        assertNull(LatitudeValidator.validate(0.0))
        assertNull(LatitudeValidator.validate(41.0082)) // Istanbul
    }

    @Test fun `Latitude rejects out-of-range and non-finite`() {
        assertNotNull(LatitudeValidator.validate(90.0001))
        assertNotNull(LatitudeValidator.validate(-90.0001))
        assertNotNull(LatitudeValidator.validate(Double.NaN))
        assertNotNull(LatitudeValidator.validate(Double.POSITIVE_INFINITY))
    }

    @Test fun `Longitude boundaries inclusive`() {
        assertNull(LongitudeValidator.validate(-180.0))
        assertNull(LongitudeValidator.validate(180.0))
        assertNull(LongitudeValidator.validate(28.9784)) // Istanbul
    }

    @Test fun `Longitude rejects out-of-range and non-finite`() {
        assertNotNull(LongitudeValidator.validate(180.0001))
        assertNotNull(LongitudeValidator.validate(-180.0001))
        assertNotNull(LongitudeValidator.validate(Double.NaN))
        assertNotNull(LongitudeValidator.validate(Double.NEGATIVE_INFINITY))
    }

    // CreditCardNumberValidator
    @Test fun `CreditCard accepts Luhn-valid numbers`() {
        assertNull(CreditCardNumberValidator.validate("4111111111111111")) // 16-digit Visa test number
        assertNull(CreditCardNumberValidator.validate("4242424242424242"))
        assertNull(CreditCardNumberValidator.validate("4111 1111 1111 1111")) // grouped with spaces
        assertNull(CreditCardNumberValidator.validate("4111-1111-1111-1111")) // grouped with dashes
    }

    @Test fun `CreditCard rejects Luhn checksum failures`() {
        assertNotNull(CreditCardNumberValidator.validate("4111111111111112")) // last digit off by one
        assertNotNull(CreditCardNumberValidator.validate("1234567890123456"))
    }

    @Test fun `CreditCard rejects wrong length or characters`() {
        assertNotNull(CreditCardNumberValidator.validate("41111111111")) //          11 digits — too short
        assertNotNull(CreditCardNumberValidator.validate("41111111111111111111")) // 20 digits — too long
        assertNotNull(CreditCardNumberValidator.validate("4111a11111111111"))
        assertNotNull(CreditCardNumberValidator.validate(""))
        assertNotNull(CreditCardNumberValidator.validate("    ")) //                 separators only, no digits
    }
}
