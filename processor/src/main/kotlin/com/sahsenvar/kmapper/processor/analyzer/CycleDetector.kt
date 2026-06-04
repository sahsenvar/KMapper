package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Detects guaranteed-infinite mapping cycles at compile time.
 *
 * An edge is only included in cycle detection when it traverses a field that is:
 *  - non-nullable (so the value MUST exist — nullable is always break-able at runtime)
 *  - not a collection (a collection can be empty, so recursion terminates)
 *
 * Examples:
 *  - `A -> B -> A` through non-null fields  → ERROR (cycle)
 *  - `Cat(parent: Cat?)`                    → OK  (nullable breaks the cycle)
 *  - `Node(children: List<Node>)`           → OK  (collection breaks the cycle)
 */
class CycleDetector(private val logger: KSPLogger) {

    fun check(mappedSources: List<KSClassDeclaration>) {
        // Build a set of FQNs that have @MapTo annotations
        val fqns = mappedSources.mapNotNull { it.qualifiedName?.asString() }.toSet()

        // Build the directed edge graph: source FQN -> list of target FQNs reachable via
        // unconditional (non-null, non-collection) fields that also have @MapTo
        val edges: Map<String, List<String>> = mappedSources.associate { decl ->
            val fqn = decl.qualifiedName!!.asString()
            fqn to (decl.primaryConstructor?.parameters
                ?.filter { p ->
                    val resolvedType = p.type.resolve()
                    !resolvedType.isMarkedNullable && !resolvedType.isCollection()
                }
                ?.mapNotNull { p ->
                    p.type.resolve().declaration.qualifiedName?.asString()
                }
                ?.filter { it in fqns }
                ?: emptyList())
        }

        // DFS cycle detection
        val visiting = mutableSetOf<String>()
        val done = mutableSetOf<String>()
        val stack = ArrayDeque<String>()

        fun dfs(node: String) {
            if (node in done) return
            if (node in visiting) {
                val cycle = stack.toList().dropWhile { it != node } + node
                logger.error(
                    "Mapping cycle detected: ${cycle.joinToString(" -> ")}. " +
                            "This would cause infinite construction at runtime. " +
                            "Break the cycle with a nullable field, a collection, or @Ignore."
                )
                return
            }
            visiting += node
            stack.addLast(node)
            edges[node]?.forEach { neighbor -> dfs(neighbor) }
            stack.removeLast()
            visiting -= node
            done += node
        }

        edges.keys.forEach { node -> dfs(node) }
    }
}

/** Returns true when this type is a standard collection or immutable collection. */
private fun KSType.isCollection(): Boolean {
    val fqn = declaration.qualifiedName?.asString() ?: return false
    return fqn in COLLECTION_FQNS || fqn.startsWith("kotlinx.collections.immutable")
}

private val COLLECTION_FQNS = setOf(
    "kotlin.collections.List",
    "kotlin.collections.MutableList",
    "kotlin.collections.Set",
    "kotlin.collections.MutableSet",
    "kotlin.collections.Collection",
    "kotlin.collections.MutableCollection",
    "kotlin.collections.Iterable",
    "kotlin.Array"
)
