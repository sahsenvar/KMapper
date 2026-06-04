@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Compiles the given sources with [MappingProcessorProvider] attached via KSP.
 * Returns both the [JvmCompilationResult] and the [KotlinCompilation] so callers
 * can inspect kspSourcesDir for generated files.
 *
 * Uses kctfork 0.12.1 (KSP2-aware fork). configureKsp{} must be called before compile()
 * so that the KSP tool is properly registered with the compilation.
 */
fun compile(vararg sources: SourceFile): Pair<JvmCompilationResult, KotlinCompilation> {
    val compilation = KotlinCompilation().apply {
        this.sources = sources.toList()
        inheritClassPath = true   // :core (annotations, MappingException, converters) on classpath
        messageOutputStream = System.out
        // Match the JVM target of :core's jvm() target so inline funs from core can be inlined.
        jvmTarget = "21"
    }
    // configureKsp {} must be called BEFORE compile() to register KSP with the compilation.
    // symbolProcessorProviders is a mutable list retrieved from the KspTool.
    compilation.configureKsp {
        @Suppress("UNCHECKED_CAST")
        (symbolProcessorProviders as MutableList).add(MappingProcessorProvider())
    }
    return compilation.compile() to compilation
}

/**
 * Reads the text of a KSP-generated file by simple name (e.g. "UserRemoteMappers.kt").
 * Walks the kspSourcesDir of this compilation.
 */
fun KotlinCompilation.generatedFile(fileName: String): String {
    val dir = kspSourcesDir
    return dir.walkTopDown()
        .firstOrNull { it.isFile && it.name == fileName }
        ?.readText()
        ?: error(
            "Generated file '$fileName' not found in $dir. " +
                    "Available: ${dir.walkTopDown().filter { it.isFile }.map { it.name }.toList()}"
        )
}
