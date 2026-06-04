package com.sahsenvar.kmapper.immutable

import com.sahsenvar.kmapper.annotations.CollectionWrapper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentList

@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()

@CollectionWrapper(forType = ImmutableList::class)
fun <T> List<T>.asImmutableList(): ImmutableList<T> = toImmutableList()

@CollectionWrapper(forType = ImmutableSet::class)
fun <T> List<T>.asImmutableSet(): ImmutableSet<T> = toImmutableSet()
