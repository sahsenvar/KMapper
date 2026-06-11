package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.reflect.KClass

/**
 * Per-field OVERRIDE of converter resolution and/or brokenness policy (both directions).
 * Never required for discovery — built-ins and @KMapperConfig converters are pair-discovered.
 * [use] left at its sentinel default means "keep the auto-discovered converter".
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ConvertWith(
    val use: KClass<out MapTypeConverter<*, *>> = MapTypeConverter::class,
    val onFail: OnFail = OnFail.Auto,
)
