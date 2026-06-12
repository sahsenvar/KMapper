package com.sahsenvar.kmapper.itest

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MapMappingTest {
    // ─── Map<String, AttrR> → Map<String, AttrD> (value-type mapping) ────────

    @Test
    fun `two-entry map maps every value`() {
        val source =
            CatalogR(
                attrs = mapOf("a" to AttrR("alpha"), "b" to AttrR("beta")),
                meta = mapOf("x" to "1", "y" to "2"),
            )
        val domain = source.toCatalogDResult().getOrThrow()
        domain.attrs shouldContainExactly mapOf("a" to AttrD("alpha"), "b" to AttrD("beta"))
    }

    @Test
    fun `empty attr map produces empty map`() {
        val domain = CatalogR(attrs = emptyMap(), meta = emptyMap()).toCatalogDResult().getOrThrow()
        domain.attrs.isEmpty() shouldBe true
    }

    @Test
    fun `single-entry map maps the value correctly`() {
        val domain =
            CatalogR(
                attrs = mapOf("key" to AttrR("v")),
                meta = emptyMap(),
            ).toCatalogDResult().getOrThrow()
        domain.attrs["key"] shouldBe AttrD("v")
    }

    // ─── Map<String, String> passthrough (keys and values preserved) ──────────

    @Test
    fun `passthrough string-to-string map is identity`() {
        val meta = mapOf("env" to "prod", "version" to "2")
        val domain = CatalogR(attrs = emptyMap(), meta = meta).toCatalogDResult().getOrThrow()
        domain.meta shouldContainExactly meta
    }

    @Test
    fun `empty passthrough map is empty`() {
        val domain = CatalogR(attrs = emptyMap(), meta = emptyMap()).toCatalogDResult().getOrThrow()
        domain.meta.isEmpty() shouldBe true
    }
}
