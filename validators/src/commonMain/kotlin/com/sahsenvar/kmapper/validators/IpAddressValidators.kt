package com.sahsenvar.kmapper.validators

import com.sahsenvar.kmapper.validation.Validator

/**
 * The value must be a strict dotted-decimal IPv4 address: four octets `0..255`,
 * no leading zeros (`192.168.001.1` is rejected — leading zeros are ambiguous octal).
 */
object Ipv4Validator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (isValidIpv4(value)) null else "must be a valid IPv4 address"
}

/**
 * The value must be a valid IPv6 address: 8 groups of 1-4 hex digits, at most one `::`
 * compression (covering at least one group), with an optional embedded IPv4 tail
 * (`::ffff:192.0.2.1`).
 */
object Ipv6Validator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (isValidIpv6(value)) null else "must be a valid IPv6 address"
}

private fun isValidIpv4(value: String): Boolean {
    val octets = value.split('.')
    if (octets.size != 4) return false
    return octets.all { octet ->
        octet.isNotEmpty() &&
            octet.length <= 3 &&
            octet.all { it in '0'..'9' } &&
            (octet.length == 1 || octet[0] != '0') &&
            octet.toInt() <= 255
    }
}

private fun isValidIpv6(value: String): Boolean {
    if (value == "::") return true
    val compressionCount = countOccurrences(value, "::")
    if (compressionCount > 1) return false

    val (head, tail) =
        if (compressionCount == 1) {
            val parts = value.split("::", limit = 2)
            parts[0] to parts[1]
        } else {
            value to null
        }

    val headGroups = if (head.isEmpty()) emptyList() else head.split(':')
    val tailGroups = if (tail.isNullOrEmpty()) emptyList() else tail.split(':')
    val groups = headGroups + tailGroups

    var groupCount = 0
    for ((index, group) in groups.withIndex()) {
        val isLast = index == groups.lastIndex
        groupCount +=
            when {
                isLast && '.' in group -> if (isValidIpv4(group)) 2 else return false

                // embedded IPv4 tail = 2 groups
                group.length in 1..4 && group.all { it.isHexDigit() } -> 1

                else -> return false
            }
    }
    // With `::` the compression must stand for at least one zero group; without it, exactly 8.
    return if (compressionCount == 1) groupCount < 8 else groupCount == 8
}

private fun countOccurrences(
    value: String,
    part: String,
): Int {
    var count = 0
    var index = value.indexOf(part)
    while (index >= 0) {
        count++
        index = value.indexOf(part, index + 1)
    }
    return count
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
