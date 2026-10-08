package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.reflect.KClass

/**
 * Direction-scoped per-field override: applies only to the @MapFrom (reverse) direction;
 * beats @ConvertWith there. Same parameter shape as [ConvertWith] — [use] left at its
 * sentinel default means "keep the auto-discovered converter".
 *
 * Typical home: a field of the `@MapFrom` class itself (the mapping's target), so a foreign
 * source class never needs annotating. A directive on the source field is a fallback.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ConvertFrom(
    val use: KClass<out MapTypeConverter<*, *>> = MapTypeConverter::class,
    val onFail: OnFail = OnFail.Auto,
)
