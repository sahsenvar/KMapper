package com.sahsenvar.kmapper.annotations

import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import com.sahsenvar.kmapper.validation.builtin.NotEmptyStringValidator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * kotlin.test-based construction checks that run on EVERY target (including iOS).
 *
 * Kotest specs in this repository execute on the JVM only (there is no Kotest native wiring),
 * so a Kotest-only commonTest leaves the native test tasks with zero discovered tests and
 * Gradle fails the build (`failOnNoDiscoveredTests`). This class keeps native test discovery
 * alive and gives the annotation contracts real native execution; the broader shape checks
 * stay in [ValidateAnnotationsTest].
 */
class AnnotationConstructionTest {
    @Test
    fun `Validate vararg preserves validator order on every target`() {
        val annotation = Validate(NotBlankValidator::class, NotEmptyStringValidator::class)
        assertEquals(
            listOf(NotBlankValidator::class, NotEmptyStringValidator::class),
            annotation.validators.toList(),
        )
    }

    @Test
    fun `ConvertWith defaults to the sentinel converter and OnFail Auto on every target`() {
        val annotation = ConvertWith()
        assertEquals(com.sahsenvar.kmapper.converter.MapTypeConverter::class, annotation.use)
        assertEquals(OnFail.Auto, annotation.onFail)
    }

    @Test
    fun `OnFail exposes exactly Auto Throw Skip on every target`() {
        assertEquals(listOf("Auto", "Throw", "Skip"), OnFail.entries.map { it.name })
    }
}
