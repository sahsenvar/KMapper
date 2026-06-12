package com.sahsenvar.kmapper.validators

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NetworkValidatorsTest {
    // Ipv4Validator
    @Test fun `Ipv4 valid addresses`() {
        assertNull(Ipv4Validator.validate("0.0.0.0")) //         all-zero boundary
        assertNull(Ipv4Validator.validate("255.255.255.255")) // all-max boundary
        assertNull(Ipv4Validator.validate("192.168.1.1"))
        assertNull(Ipv4Validator.validate("10.0.0.255"))
    }

    @Test fun `Ipv4 rejects out-of-range octets`() {
        assertNotNull(Ipv4Validator.validate("256.1.1.1"))
        assertNotNull(Ipv4Validator.validate("1.1.1.999"))
        assertNotNull(Ipv4Validator.validate("1.1.1.-1"))
    }

    @Test fun `Ipv4 rejects wrong shapes`() {
        assertNotNull(Ipv4Validator.validate("1.1.1")) //     3 octets
        assertNotNull(Ipv4Validator.validate("1.1.1.1.1")) // 5 octets
        assertNotNull(Ipv4Validator.validate("1..1.1")) //    empty octet
        assertNotNull(Ipv4Validator.validate("a.b.c.d"))
        assertNotNull(Ipv4Validator.validate(" 1.1.1.1"))
        assertNotNull(Ipv4Validator.validate(""))
    }

    @Test fun `Ipv4 rejects leading zeros - ambiguous octal`() {
        assertNotNull(Ipv4Validator.validate("01.2.3.4"))
        assertNotNull(Ipv4Validator.validate("192.168.001.1"))
        assertNull(Ipv4Validator.validate("0.2.3.4")) // a lone zero octet is fine
    }

    // Ipv6Validator
    @Test fun `Ipv6 valid addresses`() {
        assertNull(Ipv6Validator.validate("::")) //   unspecified address
        assertNull(Ipv6Validator.validate("::1")) //  loopback
        assertNull(Ipv6Validator.validate("1::")) //  trailing compression
        assertNull(Ipv6Validator.validate("2001:db8::8a2e:370:7334"))
        assertNull(Ipv6Validator.validate("2001:0db8:85a3:0000:0000:8a2e:0370:7334")) // full 8 groups
        assertNull(Ipv6Validator.validate("1:2:3:4:5:6:7::")) //  :: standing for exactly one group
        assertNull(Ipv6Validator.validate("FE80::1")) //          upper-case hex
    }

    @Test fun `Ipv6 valid with embedded IPv4 tail`() {
        assertNull(Ipv6Validator.validate("::ffff:192.0.2.1")) //          IPv4-mapped
        assertNull(Ipv6Validator.validate("1:2:3:4:5:6:192.0.2.1")) //     full form with IPv4 tail
    }

    @Test fun `Ipv6 rejects malformed addresses`() {
        assertNotNull(Ipv6Validator.validate("")) //
        assertNotNull(Ipv6Validator.validate(":")) //                  lone colon
        assertNotNull(Ipv6Validator.validate(":::")) //                triple colon
        assertNotNull(Ipv6Validator.validate("1::2::3")) //            two compressions
        assertNotNull(Ipv6Validator.validate("1:2:3:4:5:6:7")) //      7 groups, no compression
        assertNotNull(Ipv6Validator.validate("1:2:3:4:5:6:7:8:9")) //  9 groups
        assertNotNull(Ipv6Validator.validate("1:2:3:4:5:6:7:8::")) //  :: but already 8 groups
        assertNotNull(Ipv6Validator.validate("12345::")) //            5 hex digits in a group
        assertNotNull(Ipv6Validator.validate("g::1")) //               non-hex digit
        assertNotNull(Ipv6Validator.validate("::192.0.2.1:ffff")) //   IPv4 only allowed as the tail
        assertNotNull(Ipv6Validator.validate("192.0.2.1")) //          bare IPv4 is not IPv6
        assertNotNull(Ipv6Validator.validate("::ffff:192.0.2.256")) // invalid embedded IPv4
    }

    // PortNumberValidator
    @Test fun `Port boundaries`() {
        assertNull(PortNumberValidator.validate(1)) //      min usable
        assertNull(PortNumberValidator.validate(65535)) //  max
        assertNull(PortNumberValidator.validate(8080))
        assertNotNull(PortNumberValidator.validate(0)) //   "any port" is not a destination
        assertNotNull(PortNumberValidator.validate(65536))
        assertNotNull(PortNumberValidator.validate(-1))
        assertNotNull(PortNumberValidator.validate(Int.MAX_VALUE))
    }
}
