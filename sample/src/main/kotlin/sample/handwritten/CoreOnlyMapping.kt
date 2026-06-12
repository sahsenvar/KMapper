package sample.handwritten

import com.sahsenvar.kmapper.convertEachOrSkip
import com.sahsenvar.kmapper.convertOrFail
import com.sahsenvar.kmapper.convertOrNull
import com.sahsenvar.kmapper.converter.builtin.IntStringConverter
import com.sahsenvar.kmapper.converter.builtin.LongStringConverter
import com.sahsenvar.kmapper.orRequired

/**
 * HAND-WRITTEN — using `kmapper-core` alone (no annotations, no KSP).
 *
 * This is user–author parity made concrete: the SEAMS below are the exact functions generated
 * mappers call. Write a mapper by hand when you don't want code generation (a tiny module, a
 * script, full manual control) — and get the same ladder semantics, the same path-carrying
 * errors, and the same degradation reporting.
 *
 * Seam cheat-sheet (scalar):
 *   value.convertOrFail(path, from, to) { ... }   -> hard cell (no declared escape)
 *   value.convertOrNull(path, from, to) { ... }   -> nullable target (broken -> null, reported)
 *   value.orRequired(path)                        -> absence guard for same-type fields
 * Collections:
 *   list.convertEachOrSkip(path, from, to) { ... }   (and OrNull / OrFail / Set / Map variants)
 */
data class Subscriber(
    val id: Long,
    val age: Int?,
    val topics: List<Long>,
)

data class SubscriberRow(
    val id: String,
    val age: String?,
    val topics: List<String>,
)

/** Hand-written equivalent of what `@MapTo(Subscriber::class)` would generate. */
fun SubscriberRow.toSubscriberResult(): Result<Subscriber> = runCatching {
    Subscriber(
        id = id.convertOrFail("id", "kotlin.String", "kotlin.Long") { LongStringConverter.convertFrom(it) },
        age = age.convertOrNull("age", "kotlin.String", "kotlin.Int") { IntStringConverter.convertFromOrNull(it) },
        topics = topics.convertEachOrSkip("topics", "kotlin.String", "kotlin.Long") {
            LongStringConverter.convertFromOrNull(it)
        },
    )
}

fun main() = runCoreOnlyMappingDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runCoreOnlyMappingDemo() {
    val subscriber = SubscriberRow(id = "7", age = "44", topics = listOf("1", "oops", "3"))
        .toSubscriberResult()
        .getOrThrow()
    println("hand-written, same rails -> $subscriber")
    //  Subscriber(id=7, age=44, topics=[1, 3])   — the broken topic was skipped AND reported

    val broken = SubscriberRow(id = "x", age = null, topics = emptyList()).toSubscriberResult()
    println("same error discipline    -> ${broken.exceptionOrNull()?.message}")
    //  Cannot convert id: kotlin.String -> kotlin.Long
}

// `orRequired` in action for same-type fields (no conversion, just the absence guard):
fun requireName(name: String?): String = name.orRequired("name")
