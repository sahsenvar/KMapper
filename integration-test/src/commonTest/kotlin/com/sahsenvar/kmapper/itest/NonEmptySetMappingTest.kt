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
    fun `empty permissions list fails with EmptyCollection carrying the field path`() {
        val outcome = RoleR(permissions = emptyList()).toRoleDResult()
        outcome.isFailure shouldBe true
        val exception = outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.EmptyCollection>()
        exception.path shouldBe "permissions"
        exception.detail shouldBe "NonEmptySet source was empty"
    }
}
