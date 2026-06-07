@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

class CycleDetectionTest {
    @Test
    fun `unconditional cycle fails compilation`() {
        val src =
            SourceFile.kotlin(
                "Cyc.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                data class ADomain(val b: BDomain); data class BDomain(val a: ADomain)
                @MapTo(ADomain::class) data class A(val b: B)
                @MapTo(BDomain::class) data class B(val a: A)
                """.trimIndent(),
            )
        val (r, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, r.exitCode)
        assert(r.messages.contains("cycle", ignoreCase = true)) { r.messages }
    }

    @Test
    fun `nullable back-reference compiles`() {
        val src =
            SourceFile.kotlin(
                "Ok.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                data class CatDomain(val parent: CatDomain?)
                @MapTo(CatDomain::class) data class Cat(val parent: Cat?)
                """.trimIndent(),
            )
        val (r, _) = compile(src)
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
    }

    @Test
    fun `collection self-reference does not trigger cycle error`() {
        // Node has a non-null List<Node> children field — a collection edge must NOT be flagged
        // as a cycle (empty collection terminates recursion at runtime).
        // NodeDomain has the same shape; mapping is via a list of String tags to keep it simple.
        val src =
            SourceFile.kotlin(
                "Tree.kt",
                """
                import com.sahsenvar.kmapper.annotations.MapTo
                data class NodeDomain(val id: String, val tags: List<String>)
                @MapTo(NodeDomain::class) data class Node(val id: String, val tags: List<String>)
                """.trimIndent(),
            )
        val (r, _) = compile(src)
        // CycleDetector must NOT produce a cycle error — compilation succeeds
        assertEquals(KotlinCompilation.ExitCode.OK, r.exitCode, r.messages)
    }
}
