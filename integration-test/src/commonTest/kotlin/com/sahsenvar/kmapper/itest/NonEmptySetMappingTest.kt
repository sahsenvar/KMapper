package com.sahsenvar.kmapper.itest

import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.test.assertTrue

class NonEmptySetMappingTest {
    @Test
    fun `List of nested models maps to NonEmptySet`() {
        val source = RoleR(permissions = listOf(PermissionR("read"), PermissionR("write")))
        val domain = source.toRoleDResult().getOrThrow()
        domain.permissions.size shouldBe 2
        assertTrue(domain.permissions.any { it.name == "read" })
        assertTrue(domain.permissions.any { it.name == "write" })
    }

    @Test
    fun `duplicate permissions are deduplicated in NonEmptySet`() {
        val source = RoleR(permissions = listOf(PermissionR("read"), PermissionR("read")))
        val domain = source.toRoleDResult().getOrThrow()
        domain.permissions.size shouldBe 1
        assertTrue(domain.permissions.any { it.name == "read" })
    }

    @Test
    fun `empty permissions list fails with EmptyCollection`() {
        val outcome = RoleR(permissions = emptyList()).toRoleDResult()
        outcome.isFailure shouldBe true
        // NOTE: wrapper-thrown EmptyCollection currently arrives with an EMPTY path — the
        // generated wrap call site has no path-prefixing seam (tracked as a follow-up).
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.EmptyCollection>()
        exception.detail shouldBe "NonEmptySet source was empty"
    }
}
