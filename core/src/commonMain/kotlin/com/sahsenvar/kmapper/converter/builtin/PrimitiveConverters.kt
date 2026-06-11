package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter
import com.sahsenvar.kmapper.converter.UnsupportedDirection

// ---------------------------------------------------------------------------
// Built-in primitive pair objects, richer-first: S is always the richer type.
// A widening pair implements the safe poorer -> richer direction (convertFrom)
// and declares the lossy narrowing as an @UnsupportedDirection stub.
// An X-pair declares BOTH totals as annotated stubs with pair-specific reasons:
// every pair either converts or explains, at compile time, why it will not.
// ---------------------------------------------------------------------------

// ===== Numeric widening (12): real convertFrom (poorer -> richer); narrowing = annotated stub =====

/** [Short] <-> [Byte]: widens Byte -> Short; refuses the narrowing direction. */
object ShortByteConverter : MapTypeConverter<Short, Byte>(Short::class, Byte::class) {
    override fun convertFrom(target: Byte): Short = target.toShort()

    @UnsupportedDirection("Short -> Byte narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Short): Byte = unsupported()
}

/** [Int] <-> [Byte]: widens Byte -> Int; refuses the narrowing direction. */
object IntByteConverter : MapTypeConverter<Int, Byte>(Int::class, Byte::class) {
    override fun convertFrom(target: Byte): Int = target.toInt()

    @UnsupportedDirection("Int -> Byte narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Int): Byte = unsupported()
}

/** [Long] <-> [Byte]: widens Byte -> Long; refuses the narrowing direction. */
object LongByteConverter : MapTypeConverter<Long, Byte>(Long::class, Byte::class) {
    override fun convertFrom(target: Byte): Long = target.toLong()

    @UnsupportedDirection("Long -> Byte narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Long): Byte = unsupported()
}

/** [Int] <-> [Short]: widens Short -> Int; refuses the narrowing direction. */
object IntShortConverter : MapTypeConverter<Int, Short>(Int::class, Short::class) {
    override fun convertFrom(target: Short): Int = target.toInt()

    @UnsupportedDirection("Int -> Short narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Int): Short = unsupported()
}

/** [Long] <-> [Short]: widens Short -> Long; refuses the narrowing direction. */
object LongShortConverter : MapTypeConverter<Long, Short>(Long::class, Short::class) {
    override fun convertFrom(target: Short): Long = target.toLong()

    @UnsupportedDirection("Long -> Short narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Long): Short = unsupported()
}

/** [Long] <-> [Int]: widens Int -> Long; refuses the narrowing direction. */
object LongIntConverter : MapTypeConverter<Long, Int>(Long::class, Int::class) {
    override fun convertFrom(target: Int): Long = target.toLong()

    @UnsupportedDirection("Long -> Int narrows and can truncate; convert explicitly if intended.")
    override fun convertTo(source: Long): Int = unsupported()
}

/** [Float] <-> [Byte]: widens Byte -> Float exactly; refuses the lossy direction. */
object FloatByteConverter : MapTypeConverter<Float, Byte>(Float::class, Byte::class) {
    override fun convertFrom(target: Byte): Float = target.toFloat()

    @UnsupportedDirection("Float -> Byte loses precision/range.")
    override fun convertTo(source: Float): Byte = unsupported()
}

/** [Double] <-> [Byte]: widens Byte -> Double exactly; refuses the lossy direction. */
object DoubleByteConverter : MapTypeConverter<Double, Byte>(Double::class, Byte::class) {
    override fun convertFrom(target: Byte): Double = target.toDouble()

    @UnsupportedDirection("Double -> Byte loses precision/range.")
    override fun convertTo(source: Double): Byte = unsupported()
}

/** [Float] <-> [Short]: widens Short -> Float exactly; refuses the lossy direction. */
object FloatShortConverter : MapTypeConverter<Float, Short>(Float::class, Short::class) {
    override fun convertFrom(target: Short): Float = target.toFloat()

    @UnsupportedDirection("Float -> Short loses precision/range.")
    override fun convertTo(source: Float): Short = unsupported()
}

/** [Double] <-> [Short]: widens Short -> Double exactly; refuses the lossy direction. */
object DoubleShortConverter : MapTypeConverter<Double, Short>(Double::class, Short::class) {
    override fun convertFrom(target: Short): Double = target.toDouble()

    @UnsupportedDirection("Double -> Short loses precision/range.")
    override fun convertTo(source: Double): Short = unsupported()
}

/** [Double] <-> [Int]: widens Int -> Double exactly; refuses the lossy direction. */
object DoubleIntConverter : MapTypeConverter<Double, Int>(Double::class, Int::class) {
    override fun convertFrom(target: Int): Double = target.toDouble()

    @UnsupportedDirection("Double -> Int loses precision/range.")
    override fun convertTo(source: Double): Int = unsupported()
}

/** [Double] <-> [Float]: widens Float -> Double exactly; refuses the lossy direction. */
object DoubleFloatConverter : MapTypeConverter<Double, Float>(Double::class, Float::class) {
    override fun convertFrom(target: Float): Double = target.toDouble()

    @UnsupportedDirection("Double -> Float loses precision.")
    override fun convertTo(source: Double): Float = unsupported()
}

// ===== X-pairs (9): both totals are annotated unsupported() stubs =====

/** [Float] <-> [Int]: no safe direction — both refuse with a guiding reason. */
object FloatIntConverter : MapTypeConverter<Float, Int>(Float::class, Int::class) {
    @UnsupportedDirection("Float -> Int truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertTo(source: Float): Int = unsupported()

    @UnsupportedDirection("Int -> Float is lossy above 2^24 (Float has a 24-bit mantissa).")
    override fun convertFrom(target: Int): Float = unsupported()
}

/** [Float] <-> [Long]: no safe direction — both refuse with a guiding reason. */
object FloatLongConverter : MapTypeConverter<Float, Long>(Float::class, Long::class) {
    @UnsupportedDirection("Float -> Long truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertTo(source: Float): Long = unsupported()

    @UnsupportedDirection("Long -> Float is lossy above 2^24.")
    override fun convertFrom(target: Long): Float = unsupported()
}

/** [Double] <-> [Long]: no safe direction — both refuse with a guiding reason. */
object DoubleLongConverter : MapTypeConverter<Double, Long>(Double::class, Long::class) {
    @UnsupportedDirection("Double -> Long truncates the fraction; decide floor/round/ceil explicitly.")
    override fun convertTo(source: Double): Long = unsupported()

    @UnsupportedDirection("Long -> Double is lossy above 2^53 (Double has a 53-bit mantissa).")
    override fun convertFrom(target: Long): Double = unsupported()
}

/** [Byte] <-> [Boolean]: no canonical semantics in either direction. */
object ByteBooleanConverter : MapTypeConverter<Byte, Boolean>(Byte::class, Boolean::class) {
    @UnsupportedDirection("Byte -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.")
    override fun convertTo(source: Byte): Boolean = unsupported()

    @UnsupportedDirection("Boolean -> Byte has no canonical encoding (0/1? -1?). Write a custom converter.")
    override fun convertFrom(target: Boolean): Byte = unsupported()
}

/** [Short] <-> [Boolean]: no canonical semantics in either direction. */
object ShortBooleanConverter : MapTypeConverter<Short, Boolean>(Short::class, Boolean::class) {
    @UnsupportedDirection("Short -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.")
    override fun convertTo(source: Short): Boolean = unsupported()

    @UnsupportedDirection("Boolean -> Short has no canonical encoding (0/1? -1?). Write a custom converter.")
    override fun convertFrom(target: Boolean): Short = unsupported()
}

/** [Int] <-> [Boolean]: no canonical semantics in either direction. */
object IntBooleanConverter : MapTypeConverter<Int, Boolean>(Int::class, Boolean::class) {
    @UnsupportedDirection("Int -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.")
    override fun convertTo(source: Int): Boolean = unsupported()

    @UnsupportedDirection("Boolean -> Int has no canonical encoding (0/1? -1?). Write a custom converter.")
    override fun convertFrom(target: Boolean): Int = unsupported()
}

/** [Long] <-> [Boolean]: no canonical semantics in either direction. */
object LongBooleanConverter : MapTypeConverter<Long, Boolean>(Long::class, Boolean::class) {
    @UnsupportedDirection("Long -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.")
    override fun convertTo(source: Long): Boolean = unsupported()

    @UnsupportedDirection("Boolean -> Long has no canonical encoding (0/1? -1?). Write a custom converter.")
    override fun convertFrom(target: Boolean): Long = unsupported()
}

/** [Float] <-> [Boolean]: no canonical semantics in either direction. */
object FloatBooleanConverter : MapTypeConverter<Float, Boolean>(Float::class, Boolean::class) {
    @UnsupportedDirection("Float -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.")
    override fun convertTo(source: Float): Boolean = unsupported()

    @UnsupportedDirection("Boolean -> Float has no canonical encoding (0/1? -1?). Write a custom converter.")
    override fun convertFrom(target: Boolean): Float = unsupported()
}

/** [Double] <-> [Boolean]: no canonical semantics in either direction. */
object DoubleBooleanConverter : MapTypeConverter<Double, Boolean>(Double::class, Boolean::class) {
    @UnsupportedDirection("Double -> Boolean has no canonical semantics (is 2 true?). Write a custom converter.")
    override fun convertTo(source: Double): Boolean = unsupported()

    @UnsupportedDirection("Boolean -> Double has no canonical encoding (0/1? -1?). Write a custom converter.")
    override fun convertFrom(target: Boolean): Double = unsupported()
}

// ===== String pairs (7): format total / parse throws on malformed (rides the ladder) =====

/** [Byte] <-> [String]: format via toString, strict decimal parse. */
object ByteStringConverter : MapTypeConverter<Byte, String>(Byte::class, String::class) {
    override fun convertTo(source: Byte): String = source.toString()

    override fun convertFrom(target: String): Byte = target.toByte()
}

/** [Short] <-> [String]: format via toString, strict decimal parse. */
object ShortStringConverter : MapTypeConverter<Short, String>(Short::class, String::class) {
    override fun convertTo(source: Short): String = source.toString()

    override fun convertFrom(target: String): Short = target.toShort()
}

/** [Int] <-> [String]: format via toString, strict decimal parse. */
object IntStringConverter : MapTypeConverter<Int, String>(Int::class, String::class) {
    override fun convertTo(source: Int): String = source.toString()

    override fun convertFrom(target: String): Int = target.toInt()
}

/** [Long] <-> [String]: format via toString, strict decimal parse. */
object LongStringConverter : MapTypeConverter<Long, String>(Long::class, String::class) {
    override fun convertTo(source: Long): String = source.toString()

    override fun convertFrom(target: String): Long = target.toLong()
}

/** [Float] <-> [String]: format via toString, strict parse. */
object FloatStringConverter : MapTypeConverter<Float, String>(Float::class, String::class) {
    override fun convertTo(source: Float): String = source.toString()

    override fun convertFrom(target: String): Float = target.toFloat()
}

/** [Double] <-> [String]: format via toString, strict parse. */
object DoubleStringConverter : MapTypeConverter<Double, String>(Double::class, String::class) {
    override fun convertTo(source: Double): String = source.toString()

    override fun convertFrom(target: String): Double = target.toDouble()
}

/** [Boolean] <-> [String]: format via toString; parse accepts exactly "true"/"false". */
object BooleanStringConverter : MapTypeConverter<Boolean, String>(Boolean::class, String::class) {
    override fun convertTo(source: Boolean): String = source.toString()

    override fun convertFrom(target: String): Boolean = target.toBooleanStrict()
}
