package com.sahsenvar.kmapper

/**
 * Element ladder, `List<T>` default (skip rung): null element → skip +
 * [MappingDegradation.DroppedNullElement] (free filterNotNull); broken element → skip +
 * [MappingDegradation.DroppedBrokenElement] whose cause is the TYPED exception from
 * [toMappingException] at the indexed path; sanctioned null → silent skip. Relative order
 * is preserved; element failure never escalates to the container.
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
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else {
            try {
                convert(element)?.let(result::add)
            } catch (cause: Throwable) {
                val typedCause = toMappingException("$path[$index]", from, to, cause)
                reportDegradation(MappingDegradation.DroppedBrokenElement("$path[$index]", typedCause))
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
        } catch (cause: Throwable) {
            val typedCause = toMappingException("$path[$index]", from, to, cause)
            reportDegradation(MappingDegradation.AbsorbedConversionError("$path[$index]", from, to, typedCause))
            null
        }
    }
}

/**
 * `OnFail.Throw` on `List<T>`: broken element → HARD typed [MappingException] at the indexed
 * path (via [convertOrFail]); null element STILL skips with
 * [MappingDegradation.DroppedNullElement] — absence stays type-driven under Throw.
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
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else {
            result.add(element.convertOrFail("$path[$index]", from, to, convert))
        }
    }
    return result
}

/**
 * `OnFail.Throw` on `List<T?>` ("optional but validated elements"): broken element → HARD
 * typed [MappingException] at the indexed path; null element → null pass-through, silent.
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
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else {
            try {
                val converted = convert(element)
                if (converted != null && !result.add(converted)) {
                    reportDegradation(MappingDegradation.ConvergedDuplicateElement("$path[$index]"))
                }
            } catch (cause: Throwable) {
                val typedCause = toMappingException("$path[$index]", from, to, cause)
                reportDegradation(MappingDegradation.DroppedBrokenElement("$path[$index]", typedCause))
            }
        }
    }
    return result
}

/**
 * `OnFail.Throw` to `Set<T>`: broken element → HARD typed [MappingException] at the indexed
 * path; null element skips with report; post-conversion convergence is reported.
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
            reportDegradation(MappingDegradation.DroppedNullElement("$path[$index]"))
        } else if (!result.add(element.convertOrFail("$path[$index]", from, to, convert))) {
            reportDegradation(MappingDegradation.ConvergedDuplicateElement("$path[$index]"))
        }
    }
    return result
}

/**
 * `Map<K, V>` default: each entry's key and value convert on their own ladders at
 * `path["key"]`. Broken key/value → drop entry + [MappingDegradation.DroppedBrokenElement]
 * with the TYPED cause; null source value → drop + [MappingDegradation.DroppedNullElement];
 * sanctioned-null key/value → silent drop; post-conversion key collision → last-wins +
 * [MappingDegradation.DuplicateKey].
 */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesOrSkip(
    path: String,
    convertKey: (KS) -> KT?,
    convertValue: (VS) -> VT?,
): Map<KT, VT> {
    val result = LinkedHashMap<KT, VT>()
    for ((entryKey, entryValue) in this) {
        val entryPath = "$path[\"$entryKey\"]"
        val convertedKey = try {
            convertKey(entryKey) ?: continue // sanctioned-null key → silent drop
        } catch (cause: Throwable) {
            val typedCause = toMappingException(entryPath, "key", "key", cause)
            reportDegradation(MappingDegradation.DroppedBrokenElement(entryPath, typedCause))
            continue
        }
        if (entryValue == null) {
            reportDegradation(MappingDegradation.DroppedNullElement(entryPath))
            continue
        }
        val convertedValue = try {
            convertValue(entryValue) ?: continue // sanctioned-null value → silent drop
        } catch (cause: Throwable) {
            val typedCause = toMappingException(entryPath, "value", "value", cause)
            reportDegradation(MappingDegradation.DroppedBrokenElement(entryPath, typedCause))
            continue
        }
        if (result.put(convertedKey, convertedValue) != null) {
            reportDegradation(MappingDegradation.DuplicateKey(entryPath, entryKey.toString()))
        }
    }
    return result
}

/**
 * `Map<K, V>`, `OnFail.Throw`: broken key/value → HARD typed [MappingException] at the entry
 * path (interim type labels "key"/"value" until codegen supplies real pairs); null source
 * value skips with report (absence stays type-driven); key collision → last-wins + report.
 */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesOrFail(
    path: String,
    convertKey: (KS) -> KT,
    convertValue: (VS) -> VT,
): Map<KT, VT> {
    val result = LinkedHashMap<KT, VT>()
    for ((entryKey, entryValue) in this) {
        val entryPath = "$path[\"$entryKey\"]"
        val convertedKey = entryKey.convertOrFail(entryPath, "key", "key", convertKey)
        if (entryValue == null) {
            reportDegradation(MappingDegradation.DroppedNullElement(entryPath))
            continue
        }
        val convertedValue = entryValue.convertOrFail(entryPath, "value", "value", convertValue)
        if (result.put(convertedKey, convertedValue) != null) {
            reportDegradation(MappingDegradation.DuplicateKey(entryPath, entryKey.toString()))
        }
    }
    return result
}

/**
 * `Map<K, V?>` (nullable target values): null source value → null-in-place, silent; broken
 * value → null-in-place + [MappingDegradation.AbsorbedConversionError] with the TYPED cause;
 * broken key → drop entry + [MappingDegradation.DroppedBrokenElement]; sanctioned-null key →
 * silent drop; key collision → reported, value still written (last wins).
 */
inline fun <KS : Any, VS : Any, KT : Any, VT : Any> Map<KS, VS?>.convertEntriesValueOrNull(
    path: String,
    convertKey: (KS) -> KT?,
    convertValue: (VS) -> VT?,
): Map<KT, VT?> {
    val result = LinkedHashMap<KT, VT?>()
    for ((entryKey, entryValue) in this) {
        val entryPath = "$path[\"$entryKey\"]"
        val convertedKey = try {
            convertKey(entryKey) ?: continue // sanctioned-null key → silent drop
        } catch (cause: Throwable) {
            val typedCause = toMappingException(entryPath, "key", "key", cause)
            reportDegradation(MappingDegradation.DroppedBrokenElement(entryPath, typedCause))
            continue
        }
        val convertedValue = if (entryValue == null) {
            null
        } else {
            try {
                convertValue(entryValue)
            } catch (cause: Throwable) {
                val typedCause = toMappingException(entryPath, "value", "value", cause)
                reportDegradation(MappingDegradation.AbsorbedConversionError(entryPath, "value", "value", typedCause))
                null
            }
        }
        if (result.containsKey(convertedKey)) {
            reportDegradation(MappingDegradation.DuplicateKey(entryPath, entryKey.toString()))
        }
        result[convertedKey] = convertedValue
    }
    return result
}
