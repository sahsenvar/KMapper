@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.OutputStream

/**
 * Under KSP's incremental processing an output is kept only while it is tied to its inputs. Mapper
 * files used to declare no originating file, so after an upstream ABI change KSP reprocessed only
 * the dirty files and dropped every mapper it did not regenerate. Incremental KSP cannot run in
 * kctfork, so this checks the declared dependencies themselves.
 */
class OriginatingFilesTest :
    BehaviorSpec({

        given("mappings spread over several files, a @MapFrom sharing a receiver, and a @KMapperConfig") {
            val recordedDependencies = mutableMapOf<String, RecordedDependencies>()
            val (result, _) =
                compile(
                    sources =
                    listOf(
                        SourceFile.kotlin(
                            "Account.kt",
                            """
                                import com.sahsenvar.kmapper.annotations.MapTo
                                @MapTo(AccountDomainModel::class)
                                data class AccountDataModel(val id: String, val balance: Long)
                            """.trimIndent(),
                        ),
                        SourceFile.kotlin("AccountDomain.kt", "data class AccountDomainModel(val id: String, val balance: Money)"),
                        SourceFile.kotlin(
                            "AccountUi.kt",
                            """
                                import com.sahsenvar.kmapper.annotations.MapFrom
                                @MapFrom(AccountDataModel::class)
                                data class AccountUiModel(val id: String)
                            """.trimIndent(),
                        ),
                        SourceFile.kotlin(
                            "Config.kt",
                            """
                                import com.sahsenvar.kmapper.annotations.KMapperConfig
                                @KMapperConfig(converters = [MoneyConverter::class])
                                object MappingConfig
                            """.trimIndent(),
                        ),
                        SourceFile.kotlin(
                            "Money.kt",
                            """
                                import com.sahsenvar.kmapper.converter.MapTypeConverter
                                data class Money(val cents: Long)
                                object MoneyConverter : MapTypeConverter<Long, Money>(Long::class, Money::class) {
                                    override fun convertTo(source: Long): Money = Money(source)

                                    override fun convertFrom(target: Money): Long = target.cents
                                }
                            """.trimIndent(),
                        ),
                    ),
                    kspOptions = emptyMap(),
                    mappingProvider = RecordingMappingProcessorProvider(recordedDependencies),
                )

            `when`("the mapper file is written") {
                then("it is aggregating and names every source file it depends on") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    val dependencies = recordedDependencies.getValue("AccountDataModelMappers")
                    dependencies.aggregating shouldBe true
                    dependencies.originatingFileNames shouldContainExactlyInAnyOrder
                        listOf("Account.kt", "AccountDomain.kt", "AccountUi.kt", "Config.kt", "Money.kt")
                }
            }
        }
    })

/** What a written file declared, captured while the KSP session is alive (KSFiles expire with it). */
private data class RecordedDependencies(
    val aggregating: Boolean,
    val originatingFileNames: List<String>,
)

/** Runs the real [MappingProcessor] with a [CodeGenerator] that records each file's dependencies. */
private class RecordingMappingProcessorProvider(
    private val recordedDependencies: MutableMap<String, RecordedDependencies>,
) : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = MappingProcessor(
        codeGenerator = RecordingCodeGenerator(environment.codeGenerator, recordedDependencies),
        logger = environment.logger,
        options = environment.options,
    )
}

private class RecordingCodeGenerator(
    private val delegate: CodeGenerator,
    private val recordedDependencies: MutableMap<String, RecordedDependencies>,
) : CodeGenerator by delegate {
    override fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): OutputStream {
        recordedDependencies[fileName] = RecordedDependencies(dependencies.aggregating, dependencies.originatingFiles.map { it.fileName })
        return delegate.createNewFile(dependencies, packageName, fileName, extensionName)
    }
}
