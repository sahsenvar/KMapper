@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

class ConverterConfigTest {

    /**
     * Baseline: @KMapperConfig with a single converter (HexIntConverter) compiles and
     * the generated file references that converter.
     */
    @Test
    fun `@KMapperConfig converter is applied`() {
        val hexOnly = SourceFile.kotlin(
            "HexConverter.kt", """
            import com.sahsenvar.kmapper.converter.MapTypeConverter

            object HexIntConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                override fun convertToNonNull(v: String): Int = v.toInt(16)
                override fun convertFromNonNull(v: Int): String = v.toString(16)
            }
        """.trimIndent()
        )
        val model = SourceFile.kotlin(
            "M.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.KMapperConfig

            @KMapperConfig(converters = [HexIntConverter::class])
            object Cfg

            data class ItemDomain(val code: Int)

            @MapTo(ItemDomain::class)
            data class ItemRemote(val code: String)
        """.trimIndent()
        )
        val (r, compilation) = compile(hexOnly, model)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("ItemRemoteMappers.kt")
        assert(gen.contains("HexIntConverter")) { "Expected HexIntConverter in generated:\n$gen" }
    }

    /**
     * HEADLINE FEATURE TEST:
     *
     * Global converter IsoInstant (String→Instant, registered via @KMapperConfig) handles the
     * default field, while EpochInstant (also String→Instant, SAME type pair) is used ONLY for
     * the 'legacy' field via @UseMapTypeConverter.
     *
     * Before the fix the validator would hard-error because it scanned all MapTypeConverter
     * subclasses and found two String→Instant converters. After the fix the validator only
     * checks the @KMapperConfig list itself — EpochInstant is exempt because it is referenced
     * only via @UseMapTypeConverter (explicit, unambiguous).
     *
     * Assertions:
     *   - Compilation succeeds (exit code OK)
     *   - Generated EvRemoteMappers.kt uses IsoInstant for 'startsAt'
     *   - Generated EvRemoteMappers.kt uses EpochInstant for 'legacy'
     */
    @Test
    fun `per-field @UseMapTypeConverter allows same-pair converter alongside global`() {
        val converters = SourceFile.kotlin(
            "Converters.kt", """
            import com.sahsenvar.kmapper.converter.MapTypeConverter

            /** Global default: ISO-8601 string → Instant */
            object IsoInstant : MapTypeConverter<String, Long>(String::class, Long::class) {
                override fun convertToNonNull(v: String): Long = v.toLong() + 1000L
                override fun convertFromNonNull(v: Long): String = v.toString()
            }

            /** Per-field override: epoch-millis string → Instant (SAME String→Long pair!) */
            object EpochInstant : MapTypeConverter<String, Long>(String::class, Long::class) {
                override fun convertToNonNull(v: String): Long = v.toLong()
                override fun convertFromNonNull(v: Long): String = v.toString()
            }
        """.trimIndent()
        )
        val model = SourceFile.kotlin(
            "EvRemote.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.KMapperConfig
            import com.sahsenvar.kmapper.annotations.UseMapTypeConverter

            @KMapperConfig(converters = [IsoInstant::class])
            object Cfg

            data class EvDomain(val startsAt: Long, val legacy: Long)

            @MapTo(EvDomain::class)
            data class EvRemote(
                val startsAt: String,
                @UseMapTypeConverter(EpochInstant::class) val legacy: String,
            )
        """.trimIndent()
        )
        val (r, compilation) = compile(converters, model)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
        val gen = compilation.generatedFile("EvRemoteMappers.kt")
        assert(gen.contains("IsoInstant")) {
            "Expected IsoInstant (global converter) in generated:\n$gen"
        }
        assert(gen.contains("EpochInstant")) {
            "Expected EpochInstant (per-field converter) in generated:\n$gen"
        }
    }

    /**
     * GENUINELY AMBIGUOUS CASE:
     *
     * Listing TWO converters for the same (S,T) pair inside @KMapperConfig is ambiguous —
     * the processor cannot know which one to use for undecorated fields. This MUST produce
     * a COMPILATION_ERROR with a message about duplicate/ambiguous converters.
     */
    @Test
    fun `two converters for same pair in @KMapperConfig is an error`() {
        val converters = SourceFile.kotlin(
            "DupConverters.kt", """
            import com.sahsenvar.kmapper.converter.MapTypeConverter

            object ConverterA : MapTypeConverter<String, Long>(String::class, Long::class) {
                override fun convertToNonNull(v: String): Long = v.toLong()
                override fun convertFromNonNull(v: Long): String = v.toString()
            }

            object ConverterB : MapTypeConverter<String, Long>(String::class, Long::class) {
                override fun convertToNonNull(v: String): Long = v.toLong() * 2L
                override fun convertFromNonNull(v: Long): String = v.toString()
            }
        """.trimIndent()
        )
        val model = SourceFile.kotlin(
            "AmbigModel.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo
            import com.sahsenvar.kmapper.annotations.KMapperConfig

            @KMapperConfig(converters = [ConverterA::class, ConverterB::class])
            object Cfg

            data class ADomain(val value: Long)

            @MapTo(ADomain::class)
            data class ARemote(val value: String)
        """.trimIndent()
        )
        val (r, _) = compile(converters, model)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode, r.messages)
        assert(
            r.messages.contains("DUPLICATE", ignoreCase = true) ||
                r.messages.contains("duplicate", ignoreCase = true) ||
                r.messages.contains("ambiguous", ignoreCase = true) ||
                r.messages.contains("@KMapperConfig", ignoreCase = true)
        ) { "Expected duplicate/ambiguous converter error in:\n${r.messages}" }
    }

    /**
     * When no converter exists for a type pair and no @KMapperConfig registers one,
     * the processor must emit a compilation error mentioning "no converter" or similar.
     */
    @Test
    fun `missing converter fails with clear error`() {
        val model = SourceFile.kotlin(
            "M3.kt", """
            import com.sahsenvar.kmapper.annotations.MapTo

            data class XDomain(val flag: Boolean)

            @MapTo(XDomain::class)
            data class XRemote(val flag: Int)
        """.trimIndent()
        )
        val (r, _) = compile(model)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(
            r.messages.contains("no converter", ignoreCase = true) ||
                r.messages.contains("@KMapperConfig", ignoreCase = true) ||
                r.messages.contains("UseMapTypeConverter", ignoreCase = true)
        ) { "Expected missing-converter error in:\n${r.messages}" }
    }
}
