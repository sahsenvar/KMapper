package com.sahsenvar.kmapper

/**
 * Enums opt into mapping by implementing this interface; the processor maps via [wireValue],
 * never ordinal or name. [W] is the wire type (String or Int).
 */
interface MappableEnum<W : Any> {
    val wireValue: W
}
