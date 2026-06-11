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

            /** Declares the forward direction ONLY via its OrNull variant. */
            object OrNullOnlyConverter : MapTypeConverter<String, Long>(String::class, Long::class) {
                override fun convertToOrNull(source: String): Long? = source.toLongOrNull()
            }

            /** Misplaces @UnsupportedDirection on an OrNull variant — must set orNullAnnotated. */
            object OrNullAnnotatedConverter : MapTypeConverter<Int, Boolean>(Int::class, Boolean::class) {
                override fun convertTo(source: Int): Boolean = source != 0

                @UnsupportedDirection("Misplaced on the OrNull variant on purpose.")
                override fun convertToOrNull(source: Int): Boolean? = unsupported()
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
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("an annotated widening stub provides only the safe direction and carries the declared reason") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.AnnotatedStubConverter=" +
                        "source=kotlin.Long;target=kotlin.Int;" +
                        "providesTo=false;providesFrom=true;" +
                        "reasonTo=Long to Int narrows and can truncate.;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("an X-pair converter with both totals annotated provides neither direction and carries both reasons") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.BothDirectionsUnsupportedConverter=" +
                        "source=kotlin.Float;target=kotlin.Boolean;" +
                        "providesTo=false;providesFrom=false;" +
                        "reasonTo=Float to Boolean has no meaningful interpretation.;" +
                        "reasonFrom=Boolean to Float has no meaningful interpretation.;" +
                        "orNullAnnotated=false"
                }

                then("an OrNull-only override still DECLARES its direction — the direction counts as provided") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.OrNullOnlyConverter=" +
                        "source=kotlin.String;target=kotlin.Long;" +
                        "providesTo=true;providesFrom=false;" +
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=false"
                }

                then("@UnsupportedDirection on an OrNull variant sets orNullAnnotated without unsupporting the total") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.OrNullAnnotatedConverter=" +
                        "source=kotlin.Int;target=kotlin.Boolean;" +
                        "providesTo=true;providesFrom=false;" +
                        "reasonTo=null;reasonFrom=null;" +
                        "orNullAnnotated=true"
                }

                then("a bilateral converter provides both directions") {
                    compilationResult.messages shouldContain
                        "SHAPE:fixtures.BilateralConverter=" +
                        "source=kotlin.String;target=kotlin.Double;" +
                        "providesTo=true;providesFrom=true;" +
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
    })
