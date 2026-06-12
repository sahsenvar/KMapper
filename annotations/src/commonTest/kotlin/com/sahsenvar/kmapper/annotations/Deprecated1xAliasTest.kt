@file:Suppress("DEPRECATION_ERROR")

package com.sahsenvar.kmapper.annotations

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the 1.x compatibility stubs: each ERROR-level typealias must resolve to its 2.0
 * replacement (so the IDE's ReplaceWith lands on a real type), and the standalone
 * deprecated [MapDefaultValue] must keep its exact 1.x signature.
 *
 * kotlin.test on purpose — runs on every target (see AnnotationConstructionTest).
 */
class Deprecated1xAliasTest {
    @Test
    fun ignore_aliases_IgnoreMap() {
        assertEquals(IgnoreMap::class, Ignore::class)
    }

    @Test
    fun useMapTypeConverter_aliases_ConvertWith() {
        assertEquals(ConvertWith::class, UseMapTypeConverter::class)
    }

    @Test
    fun validateFrom_and_validateTo_both_alias_Validate() {
        assertEquals(Validate::class, ValidateFrom::class)
        assertEquals(Validate::class, ValidateTo::class)
    }

    @Test
    fun mapDefaultValue_keeps_its_1x_signature() {
        val annotation = MapDefaultValue(expression = "now()")
        assertEquals("now()", annotation.expression)
    }
}
