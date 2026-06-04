package com.sahsenvar.kmapper.immutable

import com.sahsenvar.kmapper.annotations.CollectionWrapper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

@CollectionWrapper(forType = PersistentList::class)
object PersistentListWrapper {
    fun <T> wrap(items: List<T>): PersistentList<T> = items.toPersistentList()
}

@CollectionWrapper(forType = ImmutableList::class)
object ImmutableListWrapper {
    fun <T> wrap(items: List<T>): ImmutableList<T> = items.toImmutableList()
}

@CollectionWrapper(forType = ImmutableSet::class)
object ImmutableSetWrapper {
    fun <T> wrap(items: List<T>): ImmutableSet<T> = items.toImmutableSet()
}

@CollectionWrapper(forType = PersistentSet::class)
object PersistentSetWrapper {
    fun <T> wrap(items: List<T>): PersistentSet<T> = items.toPersistentSet()
}
