@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

/*
 * DESIGN GATE (empirical experiment, not a feature test):
 *
 * KMapper's redesigned default-value handling ("omit/copy") rests on one unverified assumption:
 * KSP can read KSValueParameter.hasDefault — just the boolean
 * flag, never the default VALUE — for classes compiled in a DIFFERENT module, i.e. resolved from
 * the classpath via Kotlin metadata rather than from source. All existing KMapper tests are
 * single-module, so this has never been proven.
 *
 * This test stages a controlled two-module experiment with kotlin-compile-testing (kctfork):
 *   Stage 1 — "library module": plain Kotlin compilation (no KSP) of `lib.LibDomainModel` and
 *             `lib.AllDefaultsModel`, covering every default shape (literal, function-call
 *             expression, null-on-nullable, const reference, computed expression) and both flag
 *             polarities (including a non-defaulted parameter AFTER defaulted ones).
 *   Stage 2 — "consumer module": a trivial consumer source compiled with stage 1's classesDir on
 *             the classpath, plus a probe KSP processor that resolves the library classes BY NAME
 *             (so they come from the classpath, not from the consumer's sources) and logs each
 *             constructor parameter's hasDefault flag.
 *
 * A same-module control group compiles identical sources in ONE compilation with the same probe,
 * isolating "cross-module classpath resolution" as the only experimental variable.
 *
 * The assertion outcome IS the experiment's result. Do NOT weaken assertions to force green.
 */

/** Probe KSP processor: logs `HASDEFAULT:<className>.<paramName>=<hasDefault>` per parameter. */
class HasDefaultProbeProcessor(
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var hasProbed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (hasProbed) return emptyList()
        hasProbed = true
        for (qualifiedClassName in PROBED_CLASS_NAMES) {
            val classDeclaration =
                resolver.getClassDeclarationByName(resolver.getKSNameFromString(qualifiedClassName))
            if (classDeclaration == null) {
                logger.warn("PROBE:declaration-not-found:$qualifiedClassName")
                continue
            }
            val constructorParameters = classDeclaration.primaryConstructor?.parameters.orEmpty()
            for (constructorParameter in constructorParameters) {
                val simpleClassName = classDeclaration.simpleName.asString()
                val parameterName = constructorParameter.name?.asString()
                // logger.warn is load-bearing: kctfork reliably captures warn-level output in messages; info may be dropped.
                logger.warn("HASDEFAULT:$simpleClassName.$parameterName=${constructorParameter.hasDefault}")
            }
        }
        return emptyList()
    }

    companion object {
        // Keep in sync with librarySource and expectedFlagLines — unlisted classes are silently skipped.
        val PROBED_CLASS_NAMES = listOf("lib.LibDomainModel", "lib.AllDefaultsModel")
    }
}

class HasDefaultProbeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = HasDefaultProbeProcessor(environment.logger)
}

private const val GATE_JVM_TARGET = "21" // matches :core's jvm target (see CompileTestSupport)

/** Shared compilation setup for both stages; KSP probe registration stays at the call site that needs it. */
private fun baseCompilation(
    sources: List<SourceFile>,
    classpathEntries: List<File> = emptyList(),
): KotlinCompilation = KotlinCompilation().apply {
    this.sources = sources
    inheritClassPath = true
    messageOutputStream = System.out
    jvmTarget = GATE_JVM_TARGET
    classpaths = classpathEntries
}

class CrossModuleHasDefaultGateTest :
    BehaviorSpec({

        // "Library module" sources — every default shape + both hasDefault polarities.
        val librarySource =
            SourceFile.kotlin(
                "LibModels.kt",
                """
            package lib

            const val DEFAULT_RETRIES = 3

            data class LibDomainModel(
                val id: Long,                                  // no default
                val plan: String = "FREE",                     // literal default
                val tags: List<String> = emptyList(),          // expression default (function call)
                val note: String? = null,                      // nullable with null default
                val retries: Int = DEFAULT_RETRIES,            // default referencing a const
                val middle: Double = 1.5,                      // default in the MIDDLE of the list
                val trailing: Boolean                          // no default AFTER defaulted params
            )

            data class AllDefaultsModel(
                val limit: Long = 60_000L * 2,                 // computed expression default
                val label: String = "x"
            )
                """.trimIndent(),
            )

        // "Consumer module" source — actually uses the library type so the classpath dependency is real.
        val consumerSource =
            SourceFile.kotlin(
                "Consumer.kt",
                """
            package consumer

            val marker = lib.LibDomainModel(1L, trailing = true)
                """.trimIndent(),
            )

        // One positive assertion per parameter — guards against a vacuous pass on empty messages.
        val expectedFlagLines =
            listOf(
                "HASDEFAULT:LibDomainModel.id=false",
                "HASDEFAULT:LibDomainModel.plan=true",
                "HASDEFAULT:LibDomainModel.tags=true",
                "HASDEFAULT:LibDomainModel.note=true",
                "HASDEFAULT:LibDomainModel.retries=true",
                "HASDEFAULT:LibDomainModel.middle=true",
                "HASDEFAULT:LibDomainModel.trailing=false",
                "HASDEFAULT:AllDefaultsModel.limit=true",
                "HASDEFAULT:AllDefaultsModel.label=true",
            )

        /**
         * Compiles [sources] with the probe processor attached, mirroring the KSP wiring of
         * [compile] in CompileTestSupport.kt. [libraryClasspath] holds the stage 1 classes for the
         * cross-module case and is empty for the same-module control group.
         */
        fun compileWithProbe(
            sources: List<SourceFile>,
            libraryClasspath: List<File>,
        ): JvmCompilationResult {
            val probeCompilation = baseCompilation(sources, classpathEntries = libraryClasspath)
            // configureKsp {} must be called BEFORE compile() to register KSP with the compilation.
            probeCompilation.configureKsp {
                @Suppress("UNCHECKED_CAST")
                (symbolProcessorProviders as MutableList).add(HasDefaultProbeProvider())
            }
            return probeCompilation.compile()
        }

        given("a library with defaulted constructor parameters compiled separately (stage 1, no KSP)") {
            val libraryCompilation = baseCompilation(sources = listOf(librarySource))
            val libraryResult = libraryCompilation.compile()

            `when`("a consumer module compiles against its classes with the hasDefault probe (stage 2)") {
                val consumerResult =
                    compileWithProbe(
                        sources = listOf(consumerSource),
                        libraryClasspath = listOf(libraryCompilation.classesDir),
                    )

                then("both stages compile successfully") {
                    libraryResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    consumerResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                }

                then("both library classes are resolvable from the classpath") {
                    consumerResult.messages shouldNotContain "declaration-not-found"
                    consumerResult.messages shouldContain "HASDEFAULT:"
                }

                then("every parameter's hasDefault flag is readable cross-module with the correct polarity") {
                    val probeFlagLines =
                        consumerResult.messages
                            .lines()
                            .filter { "HASDEFAULT:" in it }
                            .map { probeLine -> probeLine.substring(probeLine.indexOf("HASDEFAULT:")) }
                    probeFlagLines shouldContainAll expectedFlagLines
                }
            }
        }

        given("same-module control group (library and consumer sources in ONE compilation)") {
            `when`("the identical sources compile together with the same probe") {
                val controlResult =
                    compileWithProbe(
                        sources = listOf(librarySource, consumerSource),
                        libraryClasspath = emptyList(),
                    )

                then("the compilation succeeds and both classes are found") {
                    controlResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    controlResult.messages shouldNotContain "declaration-not-found"
                }

                then("the probe reports identical flags from source — isolating cross-module as the only variable") {
                    val probeFlagLines =
                        controlResult.messages
                            .lines()
                            .filter { "HASDEFAULT:" in it }
                            .map { probeLine -> probeLine.substring(probeLine.indexOf("HASDEFAULT:")) }
                    probeFlagLines shouldContainAll expectedFlagLines
                }
            }
        }
    })
