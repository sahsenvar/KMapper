package com.sahsenvar.kmapper.arrow

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.annotations.CollectionWrapper

@CollectionWrapper(forType = NonEmptyList::class)
object NonEmptyListWrapper {
    fun <T> wrap(items: List<T>): NonEmptyList<T> =
        items.toNonEmptyListOrNull()
            ?: throw MappingException.EmptyCollection("NonEmptyList source was empty")
}
