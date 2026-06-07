@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.sahsenvar.kmapper.uuid

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.uuid.Uuid

class JavaUuidConverterTest {
    private val sample = "550e8400-e29b-41d4-a716-446655440000"

    @Test fun `JavaStringUuidConverter round-trip`() {
        val javaUuid = JavaStringUuidConverter.convertToNonNull(sample)
        JavaStringUuidConverter.convertFromNonNull(javaUuid) shouldBe sample
    }

    @Test fun `KotlinJavaUuidConverter round-trip`() {
        val kotlinUuid = Uuid.parse(sample)
        val javaUuid = KotlinJavaUuidConverter.convertToNonNull(kotlinUuid)
        KotlinJavaUuidConverter.convertFromNonNull(javaUuid).toString() shouldBe sample
    }

    @Test fun `KotlinJavaUuidConverter toString consistency`() {
        val kotlinUuid = Uuid.parse(sample)
        val javaUuid = KotlinJavaUuidConverter.convertToNonNull(kotlinUuid)
        javaUuid.toString().lowercase() shouldBe sample
    }
}
