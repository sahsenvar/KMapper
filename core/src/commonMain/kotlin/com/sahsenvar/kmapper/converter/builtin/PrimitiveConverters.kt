package com.sahsenvar.kmapper.converter.builtin

import com.sahsenvar.kmapper.converter.MapTypeConverter

object StringIntConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String): Int = value.toInt()
    override fun convertFromNonNull(value: Int): String = value.toString()
}

object StringLongConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
    override fun convertToNonNull(value: String): Long = value.toLong()
    override fun convertFromNonNull(value: Long): String = value.toString()
}

object StringDoubleConverter : MapTypeConverter<String, Double>(String::class, Double::class) {
    override fun convertToNonNull(value: String): Double = value.toDouble()
    override fun convertFromNonNull(value: Double): String = value.toString()
}

object StringFloatConverter : MapTypeConverter<String, Float>(String::class, Float::class) {
    override fun convertToNonNull(value: String): Float = value.toFloat()
    override fun convertFromNonNull(value: Float): String = value.toString()
}

object StringBooleanConverter : MapTypeConverter<String, Boolean>(String::class, Boolean::class) {
    override fun convertToNonNull(value: String): Boolean = value.toBoolean()
    override fun convertFromNonNull(value: Boolean): String = value.toString()
}

object IntLongConverter : MapTypeConverter<Int, Long>(Int::class, Long::class) {
    override fun convertToNonNull(value: Int): Long = value.toLong()
    override fun convertFromNonNull(value: Long): Int {
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Long value $value is out of Int range"
        }
        return value.toInt()
    }
}
