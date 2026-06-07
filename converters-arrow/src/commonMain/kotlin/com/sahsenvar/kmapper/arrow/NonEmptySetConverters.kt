package com.sahsenvar.kmapper.arrow

import arrow.core.NonEmptySet
import arrow.core.toNonEmptySetOrNull
import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.annotations.CollectionWrapper

@CollectionWrapper(forType = NonEmptySet::class)
object NonEmptySetWrapper {
    fun <T> wrap(items: List<T>): NonEmptySet<T> = items.toNonEmptySetOrNull()
        ?: throw MappingException.EmptyCollection("NonEmptySet source was empty")
}
