package sample.config

import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.arrow.NonEmptyListWrapper
import com.sahsenvar.kmapper.immutable.PersistentListWrapper
import com.sahsenvar.kmapper.immutable.PersistentSetWrapper
import sample.converters.MoneyStringConverter

/**
 * Module-wide mapping configuration — the pattern a real app uses.
 *
 * - `converters`: pair-keyed AUTO-DISCOVERY. Registering [MoneyStringConverter] here means every
 *   `Money <-> String` field in this module converts automatically, no per-field annotation.
 *   (A `@KMapperConfig` converter also SHADOWS a built-in for the same pair — your house format wins.)
 * - `wrappers`: custom container support. Each wrapper object teaches the compiler how to build
 *   (and unwrap) a non-stdlib collection like `PersistentList` or `NonEmptyList`.
 *
 * Registering the same type pair twice is a compile-time error — even with flipped orientation.
 */
@KMapperConfig(
    converters = [MoneyStringConverter::class],
    wrappers = [PersistentListWrapper::class, PersistentSetWrapper::class, NonEmptyListWrapper::class],
)
object MappingConfig
