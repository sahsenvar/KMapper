package com.sahsenvar.kmapper

import kotlin.coroutines.cancellation.CancellationException

/**
 * Element ladder, `List<T>` default (skip rung): null element → skip +
 * [MappingDegradation.DroppedNullElement] (free filterNotNull); broken element → skip +
 * [MappingDegradation.DroppedBrokenElement] whose cause is the TYPED exception from
 * [toMappingException] at the indexed path; sanctioned null → silent skip. Relative order
 * is preserved; element failure never escalates to the container.
 * Only [Exception]s are absorbed: [CancellationException] and [Error]s always propagate.
 */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrSkip(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): List<T> {
    val result = ArrayList<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation { MappingDegradation.DroppedNullElement("$path[$index]") }
        } else {
            try {
                convert(element)?.let(result::add)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (cause: Exception) {
                reportDegradation {
                    val elementPath = "$path[$index]"
                    MappingDegradation.DroppedBrokenElement(elementPath, toMappingException(elementPath, from, to, cause))
                }
            }
        }
    }
    return result
}

/**
 * Element ladder, `List<T?>` default (alignment preserved): null element → null pass-through,
 * silent; broken element → null-in-place + [MappingDegradation.AbsorbedConversionError] whose
 * cause is the TYPED exception from [toMappingException]. Length and index alignment are
 * preserved.
 * Only [Exception]s are absorbed: [CancellationException] and [Error]s always propagate.
 */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrNull(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): List<T?> = mapIndexed { index, element ->
    if (element == null) {
        null
    } else {
        try {
            convert(element)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Exception) {
            reportDegradation {
                val elementPath = "$path[$index]"
                MappingDegradation.AbsorbedConversionError(elementPath, from, to, toMappingException(elementPath, from, to, cause))
            }
            null
        }
    }
}

/**
 * `OnFail.Throw` on `List<T>`: broken element → HARD typed [MappingException] at the indexed
 * path; null element STILL skips with [MappingDegradation.DroppedNullElement] — absence stays
 * type-driven under Throw. [CancellationException] rethrows untouched.
 */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrFail(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T,
): List<T> {
    val result = ArrayList<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation { MappingDegradation.DroppedNullElement("$path[$index]") }
        } else {
            result.add(
                try {
                    convert(element)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (cause: Throwable) {
                    throw toMappingException("$path[$index]", from, to, cause)
                },
            )
        }
    }
    return result
}

/**
 * `OnFail.Throw` on `List<T?>` ("optional but validated elements"): broken element → HARD
 * typed [MappingException] at the indexed path; null element → null pass-through, silent.
 * [CancellationException] rethrows untouched.
 */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrNullStrict(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): List<T?> = mapIndexed { index, element ->
    element?.let { source ->
        try {
            convert(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Throwable) {
            throw toMappingException("$path[$index]", from, to, cause)
        }
    }
}

/**
 * `Set<T>` default: always skip (null-in-place is degenerate in a set) — null element →
 * skip + report; broken element → skip + [MappingDegradation.DroppedBrokenElement] with the
 * TYPED cause; post-conversion convergence (distinct sources → same target) →
 * [MappingDegradation.ConvergedDuplicateElement] at the LATER element's path. Survivors keep
 * insertion order (LinkedHashSet).
 * Only [Exception]s are absorbed: [CancellationException] and [Error]s always propagate.
 */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrSkipToSet(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T?,
): Set<T> {
    val result = LinkedHashSet<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation { MappingDegradation.DroppedNullElement("$path[$index]") }
        } else {
            try {
                val converted = convert(element)
                if (converted != null && !result.add(converted)) {
                    reportDegradation { MappingDegradation.ConvergedDuplicateElement("$path[$index]") }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (cause: Exception) {
                reportDegradation {
                    val elementPath = "$path[$index]"
                    MappingDegradation.DroppedBrokenElement(elementPath, toMappingException(elementPath, from, to, cause))
                }
            }
        }
    }
    return result
}

/**
 * `OnFail.Throw` to `Set<T>`: broken element → HARD typed [MappingException] at the indexed
 * path; null element skips with report; post-conversion convergence is reported.
 * [CancellationException] rethrows untouched.
 */
inline fun <S : Any, T : Any> Iterable<S?>.convertEachOrFailToSet(
    path: String,
    from: String,
    to: String,
    convert: (S) -> T,
): Set<T> {
    val result = LinkedHashSet<T>()
    forEachIndexed { index, element ->
        if (element == null) {
            reportDegradation { MappingDegradation.DroppedNullElement("$path[$index]") }
        } else {
            val converted = try {
                convert(element)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (cause: Throwable) {
                throw toMappingException("$path[$index]", from, to, cause)
            }
            if (!result.add(converted)) {
                reportDegradation { MappingDegradation.ConvergedDuplicateElement("$path[$index]") }
            }
        }
    }
    return result
}

/**
 * `Map<K, V>` default: each entry's key and value convert on their own ladders at
 * `path["key"]`, with the real type pairs [keyFrom]→[keyTo] and [valueFrom]→[valueTo] carried
 * into typed causes. Broken key/value → drop entry + [MappingDegradation.DroppedBrokenElement]
 * with the TYPED cause; null source value → drop + [MappingDegradation.DroppedNullElement];
 * sanctioned-null key/value → silent drop; post-conversion key collision → last-wins +
 * [MappingDegradation.DuplicateKey].
 * Only [Exception]s are absorbed: [CancellationException] and [Error]s always propagate.
 */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesOrSkip(
    path: String,
    keyFrom: String,
    keyTo: String,
    valueFrom: String,
    valueTo: String,
    convertKey: (KS) -> KT?,
    convertValue: (VS) -> VT?,
): Map<KT, VT> {
    val result = LinkedHashMap<KT, VT>()
    for ((entryKey, entryValue) in this) {
        val convertedKey = try {
            convertKey(entryKey) ?: continue // sanctioned-null key → silent drop
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Exception) {
            reportDegradation {
                val entryPath = "$path[\"$entryKey\"]"
                MappingDegradation.DroppedBrokenElement(entryPath, toMappingException(entryPath, keyFrom, keyTo, cause))
            }
            continue
        }
        if (entryValue == null) {
            reportDegradation { MappingDegradation.DroppedNullElement("$path[\"$entryKey\"]") }
            continue
        }
        val convertedValue = try {
            convertValue(entryValue) ?: continue // sanctioned-null value → silent drop
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Exception) {
            reportDegradation {
                val entryPath = "$path[\"$entryKey\"]"
                MappingDegradation.DroppedBrokenElement(entryPath, toMappingException(entryPath, valueFrom, valueTo, cause))
            }
            continue
        }
        if (result.put(convertedKey, convertedValue) != null) {
            reportDegradation { MappingDegradation.DuplicateKey("$path[\"$entryKey\"]", entryKey.toString()) }
        }
    }
    return result
}

/**
 * `Map<K, V>`, `OnFail.Throw`: broken key/value → HARD typed [MappingException] at the entry
 * path, carrying the real type pair [keyFrom]→[keyTo] or [valueFrom]→[valueTo]; null source
 * value skips with report (absence stays type-driven); key collision → last-wins + report.
 * [CancellationException] rethrows untouched.
 */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesOrFail(
    path: String,
    keyFrom: String,
    keyTo: String,
    valueFrom: String,
    valueTo: String,
    convertKey: (KS) -> KT,
    convertValue: (VS) -> VT,
): Map<KT, VT> {
    val result = LinkedHashMap<KT, VT>()
    for ((entryKey, entryValue) in this) {
        val convertedKey = try {
            convertKey(entryKey)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Throwable) {
            throw toMappingException("$path[\"$entryKey\"]", keyFrom, keyTo, cause)
        }
        if (entryValue == null) {
            reportDegradation { MappingDegradation.DroppedNullElement("$path[\"$entryKey\"]") }
            continue
        }
        val convertedValue = try {
            convertValue(entryValue)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Throwable) {
            throw toMappingException("$path[\"$entryKey\"]", valueFrom, valueTo, cause)
        }
        if (result.put(convertedKey, convertedValue) != null) {
            reportDegradation { MappingDegradation.DuplicateKey("$path[\"$entryKey\"]", entryKey.toString()) }
        }
    }
    return result
}

/**
 * `Map<K, V?>` (nullable target values): null source value → null-in-place, silent; broken
 * value → null-in-place + [MappingDegradation.AbsorbedConversionError] with the TYPED cause
 * carrying [valueFrom]→[valueTo]; broken key → drop entry +
 * [MappingDegradation.DroppedBrokenElement] carrying [keyFrom]→[keyTo]; sanctioned-null key →
 * silent drop; key collision → reported, value still written (last wins).
 * Only [Exception]s are absorbed: [CancellationException] and [Error]s always propagate.
 */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesValueOrNull(
    path: String,
    keyFrom: String,
    keyTo: String,
    valueFrom: String,
    valueTo: String,
    convertKey: (KS) -> KT?,
    convertValue: (VS) -> VT?,
): Map<KT, VT?> {
    val result = LinkedHashMap<KT, VT?>()
    for ((entryKey, entryValue) in this) {
        val convertedKey = try {
            convertKey(entryKey) ?: continue // sanctioned-null key → silent drop
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Exception) {
            reportDegradation {
                val entryPath = "$path[\"$entryKey\"]"
                MappingDegradation.DroppedBrokenElement(entryPath, toMappingException(entryPath, keyFrom, keyTo, cause))
            }
            continue
        }
        val convertedValue = if (entryValue == null) {
            null
        } else {
            try {
                convertValue(entryValue)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (cause: Exception) {
                reportDegradation {
                    val entryPath = "$path[\"$entryKey\"]"
                    MappingDegradation.AbsorbedConversionError(
                        entryPath,
                        valueFrom,
                        valueTo,
                        toMappingException(entryPath, valueFrom, valueTo, cause),
                    )
                }
                null
            }
        }
        if (result.containsKey(convertedKey)) {
            reportDegradation { MappingDegradation.DuplicateKey("$path[\"$entryKey\"]", entryKey.toString()) }
        }
        result[convertedKey] = convertedValue
    }
    return result
}
