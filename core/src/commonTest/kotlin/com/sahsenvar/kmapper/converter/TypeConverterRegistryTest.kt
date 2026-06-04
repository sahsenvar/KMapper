package com.sahsenvar.kmapper.converter

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

// Unique type-pairs per test to avoid cross-test pollution in the global registry.
// (The registry is first-write-wins, so distinct pairs make each test order-independent.)

private object StringToInt : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String) = value.toInt()
    override fun convertFromNonNull(value: Int) = value.toString()
}

private object StringToIntDuplicate : MapTypeConverter<String, Int>(String::class, Int::class) {
    override fun convertToNonNull(value: String) = -1
    override fun convertFromNonNull(value: Int) = "x"
}

private object LongToBoolean : MapTypeConverter<Long, Boolean>(Long::class, Boolean::class) {
    override fun convertToNonNull(value: Long) = value != 0L
    override fun convertFromNonNull(value: Boolean) = if (value) 1L else 0L
}

class TypeConverterRegistryTest {

    @Test
    fun `register then get returns it`() {
        TypeConverterRegistry.register(StringToInt)
        TypeConverterRegistry.has(String::class, Int::class) shouldBe true
        TypeConverterRegistry.get(String::class, Int::class) shouldBe StringToInt
    }

    @Test
    fun `second register of same pair does not overwrite (first-write-wins)`() {
        TypeConverterRegistry.register(StringToInt)
        TypeConverterRegistry.register(StringToIntDuplicate)
        TypeConverterRegistry.get(String::class, Int::class) shouldBe StringToInt
    }

    @Test
    fun `has returns true after register`() {
        TypeConverterRegistry.register(LongToBoolean)
        TypeConverterRegistry.has(Long::class, Boolean::class) shouldBe true
    }

    @Test
    fun `get unknown pair is null`() {
        TypeConverterRegistry.get(Int::class, Float::class).shouldBeNull()
    }

    @Test
    fun `has unknown pair is false`() {
        TypeConverterRegistry.has(Double::class, Char::class) shouldBe false
    }
}
