@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.sahsenvar.kmapper.processor.analyzer.ConverterIntrospector
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Unit tests for [ConverterIntrospector] — the resolution layer's "brain" that reads a
 * converter's (S, T) pair and which directions it provides from the KSP model.
 *
 * Wiring mirrors [CrossModuleHasDefaultGateTest]'s probe pattern: tiny converter fixtures are
 * compiled with a throwaway probe processor that instantiates ConverterIntrospector against the
 * test compilation's Resolver and logs every [com.sahsenvar.kmapper.processor.analyzer.ConverterShape]
 * field as one parseable line per declaration. Assertions parse the captured messages.
 */

/** Probe KSP processor: logs `SHAPE:<fqn>=<all ConverterShape fields>` (or `=null`) per probed name. */
class ConverterShapeProbeProcessor(
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var hasProbed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (hasProbed) return emptyList()
        hasProbed = true
        val introspector = ConverterIntrospector(resolver)
        for (probedFqn in PROBED_DECLARATION_NAMES) {
            val shape = introspector.shapeOf(probedFqn)
            if (shape == null) {
                // logger.warn is load-bearing: kctfork reliably captures warn-level output in messages.
                logger.warn("SHAPE:$probedFqn=null")
            } else {
                logger.warn(
                    "SHAPE:$probedFqn=" +
                        "source=${shape.sourceFqn};target=${shape.targetFqn};" +
                        "providesTo=${shape.providesTo};providesFrom=${shape.providesFrom};" +
                        "orNullOnlyTo=${shape.orNullOnlyTo};orNullOnlyFrom=${shape.orNullOnlyFrom};" +
                        "reasonTo=${shape.unsupportedToReason};reasonFrom=${shape.unsupportedFromReason};" +
                        "orNullAnnotated=${shape.orNullAnnotated}",
                )
            }
        }
        return emptyList()
    }

    companion object {
        // Keep in sync with converterFixturesSource — unlisted declarations are silently skipped.
        val PROBED_DECLARATION_NAMES = listOf(
            "fixtures.ForwardOnlyConverter",
            "fixtures.AnnotatedStubConverter",
            "fixtures.BothDirectionsUnsupportedConverter",
            "fixtures.OrNullOnlyConverter",
            "fixtures.OrNullAnnotatedConverter",
            "fixtures.TotalPlusOrNullConverter",
            "fixtures.BilateralConverter",
            "fixtures.DataModel",
            "fixtures.DoesNotExist",
        )
    }
}

class ConverterShapeProbeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = ConverterShapeProbeProcessor(environment.logger)
}

/** Compiles [sources] with the shape probe attached, mirroring the KSP wiring of [compile]. */
private fun compileWithShapeProbe(vararg sources: SourceFile): JvmCompilationResult {
    val probeCompilation =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true // :core (MapTypeConverter, UnsupportedDirection) on classpath
            messageOutputStream = System.out
            jvmTarget = "21"
        }
    // configureKsp {} must be called BEFORE compile() to register KSP with the compilation.
    probeCompilation.configureKsp {
        @Suppress("UNCHECKED_CAST")
        (symbolProcessorProviders as MutableList).add(ConverterShapeProbeProvider())
    }
    return probeCompilation.compile()
}

class ConverterIntrospectorTest :
    BehaviorSpec({

        // One fixture per shape axis. Reasons avoid ';' so the probe's one-line format stays parseable.
        val converterFixturesSource =
            SourceFile.kotlin(
                "ConverterFixtures.kt",
                """
            package fixtures

            import com.sahsenvar.kmapper.converter.MapTypeConverter
            import com.sahsenvar.kmapper.converter.UnsupportedDirection

            /** Not a converter at all — shapeOf must yield null. */
            data class DataModel(val raw: String)

            /** Declares only the total forward method. */
            object ForwardOnlyConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                override fun convertTo(source: String): Int = source.toInt()
            }

            /** Widening style: safe direction real, lossy direction an annotated stub. */
            object AnnotatedStubConverter : MapTypeConverter<Long, Int>(Long::class, Int::class) {
                override fun convertFrom(target: Int): Long = target.toLong()

                @UnsupportedDirection("Long to Int narrows and can truncate.")
                override fun convertTo(source: Long): Int = unsupported()
            }

            /** X-pair style: BOTH totals are annotated stubs with pair-specific reasons. */
            object BothDirectionsUnsupportedConverter :
                MapTypeConverter<Float, Boolean>(Float::class, Boolean::class) {
                @UnsupportedDirection("Float to Boolean has no meaningful interpretation.")
                override fun convertTo(source: Float): Boolean = unsupported()

                @UnsupportedDirection("Boolean to Float has no meaningful interpretation.")
                override fun convertFrom(target: Boolean): Float = unsupported()
            }

            /** OrNull without its total — does NOT provide the direction; flagged orNullOnlyTo. */
            object OrNullOnlyConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
                override fun convertToOrNull(source: String): Long? = source.toLongOrNull()
            }

            /** Misplaces @UnsupportedDirection on an OrNull variant — must set orNullAnnotated. */
            object OrNullAnnotatedConverter : MapTypeConverter<Int, Boolean>(Int::class, Boolean::class) {
                override fun convertTo(source: Int): Boolean = source != 0

                @UnsupportedDirection("Misplaced on the OrNull variant on purpose.")
                override fun convertToOrNull(source: Int): Boolean? = unsupported()
            }

            /** Correct sanctioned-null style: OrNull declared IN ADDITION to its total. */
            object TotalPlusOrNullConverter : MapTypeConverter<String, Int>(String::class, Int::class) {
                override fun convertTo(source: String): Int = source.toInt()

                override fun convertToOrNull(source: String): Int? = source.toIntOrNull()
            }

            /** Plain bilateral converter providing both totals. */
            object BilateralConverter : MapTypeConverter<String, Double>(String::class, Double::class) {
                override fun convertTo(source: String): Double = source.toDouble()

                override fun convertFrom(target: Double): String = target.toString()
            }
                """.trimIndent(),
            )

        given("converter fixtures covering every ConverterShape axis, compiled with the shape probe") {
            val compilationResult = compileWithShapeProbe(converterFixturesSource)

            `when`("the probe introspects each declaration through ConverterIntrospector") {
                then("the fixture compilation succeeds") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("a forward-only converter provides convertTo only, with no reasons") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.ForwardOnlyConverter=" +
                        "source=kotlin.String;target=kotlin.Int;" +
                        "providesTo=true;providesFrom=false;" +
                        "orNullOnlyTo=false;orNullOnlyFrom=false;" +
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("an annotated widening stub provides only the safe direction and carries the declared reason") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.AnnotatedStubConverter=" +
                        "source=kotlin.Long;target=kotlin.Int;" +
                        "providesTo=false;providesFrom=true;" +
                        "orNullOnlyTo=false;orNullOnlyFrom=false;" +
                        "reasonTo=Long to Int narrows and can truncate.;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("an X-pair converter with both totals annotated provides neither direction and carries both reasons") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.BothDirectionsUnsupportedConverter=" +
                        "source=kotlin.Float;target=kotlin.Boolean;" +
                        "providesTo=false;providesFrom=false;" +
                        "orNullOnlyTo=false;orNullOnlyFrom=false;" +
                        "reasonTo=Float to Boolean has no meaningful interpretation.;" +
                        "reasonFrom=Boolean to Float has no meaningful interpretation.;" +
                        "orNullAnnotated=false"
                }

                then("an OrNull-only override does NOT provide its direction and is flagged orNullOnly") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.OrNullOnlyConverter=" +
                        "source=kotlin.String;target=kotlin.Long;" +
                        "providesTo=false;providesFrom=false;" +
                        "orNullOnlyTo=true;orNullOnlyFrom=false;" +
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("@UnsupportedDirection on an OrNull variant sets orNullAnnotated without unsupporting the total") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.OrNullAnnotatedConverter=" +
                        "source=kotlin.Int;target=kotlin.Boolean;" +
                        "providesTo=true;providesFrom=false;" +
                        "orNullOnlyTo=false;orNullOnlyFrom=false;" +
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=true"
                }

                then("total plus OrNull together provide the direction with no orNullOnly flag") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.TotalPlusOrNullConverter=" +
                        "source=kotlin.String;target=kotlin.Int;" +
                        "providesTo=true;providesFrom=false;" +
                        "orNullOnlyTo=false;orNullOnlyFrom=false;" +
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("a bilateral converter provides both directions") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.BilateralConverter=" +
                        "source=kotlin.String;target=kotlin.Double;" +
                        "providesTo=true;providesFrom=true;" +
                        "orNullOnlyTo=false;orNullOnlyFrom=false;" +
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("a non-converter class yields no shape") {
                    compilationResult.messages shouldContain "SHAPE:fixtures.DataModel=null"
                }

                then("an unresolvable FQN yields no shape") {
                    compilationResult.messages shouldContain "SHAPE:fixtures.DoesNotExist=null"
                }
            }
        }

        // Resolution-level coverage (real MappingProcessor via compile()): an OrNull-only
        // override at a NEEDED direction is a guided compile error — a hard landing site
        // would otherwise call the throwing total at runtime, breaking the compile-time
        // guarantee. Total + OrNull together is the sanctioned-null style and resolves fine.
        given("a mapping whose needed direction the converter declares ONLY via its OrNull variant") {
            val orNullOnlyMappingSource =
                SourceFile.kotlin(
                    "OrNullOnlyMapping.kt",
                    """
                package fixtures

                import com.sahsenvar.kmapper.annotations.KMapperConfig
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.converter.MapTypeConverter

                /** Needed String -> Long direction declared ONLY as convertToOrNull. */
                object OrNullOnlyAmountConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
                    override fun convertToOrNull(source: String): Long? = source.toLongOrNull()
                }

                @KMapperConfig(converters = [OrNullOnlyAmountConverter::class])
                object MappingConfig

                data class DomainModel(val amount: Long)

                @MapTo(DomainModel::class)
                data class DataModel(val amount: String)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled with the real processor") {
                val (compilationResult, _) = compile(orNullOnlyMappingSource)

                then("compilation fails") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                }

                then("the error guides the author to also override the total method") {
                    compilationResult.messages shouldContain "override the total method too"
                }
            }
        }

        given("a converter declaring both the total and its OrNull variant for the needed direction") {
            val totalPlusOrNullMappingSource =
                SourceFile.kotlin(
                    "TotalPlusOrNullMapping.kt",
                    """
                package fixtures

                import com.sahsenvar.kmapper.annotations.KMapperConfig
                import com.sahsenvar.kmapper.annotations.MapTo
                import com.sahsenvar.kmapper.converter.MapTypeConverter

                /** Sanctioned-null style: OrNull in ADDITION to the total. */
                object TotalPlusOrNullAmountConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
                    override fun convertTo(source: String): Long = source.toLong()

                    override fun convertToOrNull(source: String): Long? = source.toLongOrNull()
                }

                @KMapperConfig(converters = [TotalPlusOrNullAmountConverter::class])
                object MappingConfig

                data class DomainModel(val amount: Long)

                @MapTo(DomainModel::class)
                data class DataModel(val amount: String)
                    """.trimIndent(),
                )

            `when`("the mapping is compiled with the real processor") {
                val (compilationResult, _) = compile(totalPlusOrNullMappingSource)

                then("compilation succeeds — total plus OrNull is the sanctioned-null style") {
                    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }
            }
        }
    })
