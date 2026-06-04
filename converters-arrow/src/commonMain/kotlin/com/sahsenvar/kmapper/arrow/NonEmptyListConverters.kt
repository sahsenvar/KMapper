package com.sahsenvar.kmapper.arrow

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.annotations.CollectionWrapper

@CollectionWrapper(forType = NonEmptyList::class)
fun <T> List<T>.asNonEmptyList(): NonEmptyList<T> =
    toNonEmptyListOrNull() ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
