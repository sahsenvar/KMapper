package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.converter.MapTypeConverter
import kotlin.reflect.KClass

/**
 * Direction-scoped per-field override: applies only to the @MapTo (forward) direction;
 * beats @ConvertWith there. Same parameter shape as [ConvertWith] — [use] left at its
 * sentinel default means "keep the auto-discovered converter".
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ConvertTo(
    val use: KClass<out MapTypeConverter<*, *>> = MapTypeConverter::class,
    val onFail: OnFail = OnFail.Auto,
)
