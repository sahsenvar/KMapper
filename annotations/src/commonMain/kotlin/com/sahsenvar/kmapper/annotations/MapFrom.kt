package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.KMapperWrapper
import kotlin.reflect.KClass

/**
 * Generates the plain mapper `fun Source.to{Target}(): Target` plus whatever [wrapper] adds on
 * top of it (e.g. [KMapperWrapper.KtResult] → `to{Target}Result(): Result<Target>`).
 * [KMapperWrapper.Default] defers to the module-wide setting (Gradle `KMapper { wrapper = … }`),
 * which itself defaults to [KMapperWrapper.None].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class MapFrom(
    val source: KClass<*>,
    val wrapper: KClass<out KMapperWrapper> = KMapperWrapper.Default::class,
)
