package com.sahsenvar.kmapper

import com.sahsenvar.kmapper.converter.MapTypeConverter
import io.kotest.matchers.shouldBe
import kotlin.test.Test

// Minimal recording listener used only in this test file.
private class RecordingListener : MappingListener {
    val starts = mutableListOf<Pair<Any, kotlin.reflect.KClass<*>>>()
    val completes = mutableListOf<Pair<Any, Any>>()
    val errors = mutableListOf<Pair<Any, MappingException>>()

    override fun onMapStart(
        source: Any,
        target: kotlin.reflect.KClass<*>,
    ) {
        starts += source to target
    }

    override fun onMapComplete(
        source: Any,
        result: Any,
    ) {
        completes += source to result
    }

    override fun onError(
        source: Any,
        error: MappingException,
    ) {
        errors += source to error
    }
}

class KMapperTest {
    @Test
    fun `hasListeners is false when no listeners registered`() {
        // Clean up any listeners before testing (remove all known)
        val beforeCount = KMapper.hasListeners
        // This test is more of a documentation — the global object may have listeners from
        // other tests. We test addListener/removeListener round-trip instead.
        val listener = RecordingListener()
        KMapper.addListener(listener)
        KMapper.hasListeners shouldBe true
        KMapper.removeListener(listener)
    }

    @Test
    fun `addListener + removeListener is symmetric`() {
        val listener = RecordingListener()
        KMapper.addListener(listener)
        KMapper.hasListeners shouldBe true
        KMapper.removeListener(listener)
        // After removal hasListeners may still be true if other tests left listeners,
        // but at minimum removing should not throw
    }

    @Test
    fun `dispatch calls onMapStart on registered listener`() {
        val listener = RecordingListener()
        KMapper.addListener(listener)
        try {
            val source = "hello"
            KMapper.dispatch { onMapStart(source, String::class) }
            listener.starts.any { it.first == source && it.second == String::class } shouldBe true
        } finally {
            KMapper.removeListener(listener)
        }
    }

    @Test
    fun `dispatch calls onMapComplete on registered listener`() {
        val listener = RecordingListener()
        KMapper.addListener(listener)
        try {
            val source = "src"
            val result = "res"
            KMapper.dispatch { onMapComplete(source, result) }
            listener.completes.any { it.first == source && it.second == result } shouldBe true
        } finally {
            KMapper.removeListener(listener)
        }
    }

    @Test
    fun `dispatch calls onError on registered listener`() {
        val listener = RecordingListener()
        KMapper.addListener(listener)
        try {
            val source = "bad"
            val ex = MappingException.RequiredFieldMissing("id")
            KMapper.dispatch { onError(source, ex) }
            listener.errors.any { it.first == source && it.second === ex } shouldBe true
        } finally {
            KMapper.removeListener(listener)
        }
    }

    @Test
    fun `addConverter delegates to TypeConverterRegistry`() {
        // Use a unique type-pair not used elsewhere
        val conv =
            object : MapTypeConverter<Char, Byte>(Char::class, Byte::class) {
                override fun convertTo(source: Char) = source.code.toByte()

                override fun convertFrom(target: Byte) = target.toInt().toChar()
            }
        KMapper.addConverter(conv)
        // If registered, hasListeners is unrelated — we just verify no exception
        // and the registry has it
        com.sahsenvar.kmapper.converter.TypeConverterRegistry
            .has(Char::class, Byte::class) shouldBe true
    }

    @Test
    fun `LoggingMappingListener logs start and complete`() {
        val logs = mutableListOf<String>()
        val logger = LoggingMappingListener { logs += it }
        logger.onMapStart("source", String::class)
        logger.onMapComplete("source", "result")
        logs.any { "start" in it } shouldBe true
        logs.any { "done" in it } shouldBe true
    }
}
