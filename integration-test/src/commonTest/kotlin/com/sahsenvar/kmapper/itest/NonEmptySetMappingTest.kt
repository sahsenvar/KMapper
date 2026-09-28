package com.sahsenvar.kmapper.itest

import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NonEmptySetMappingTest {
    @Test
    fun `List of nested models maps to NonEmptySet`() {
        val source = RoleR(permissions = listOf(PermissionR("read"), PermissionR("write")))
        val domain = source.toRoleD()
        domain.permissions.size shouldBe 2
        assertTrue(domain.permissions.any { it.name == "read" })
        assertTrue(domain.permissions.any { it.name == "write" })
    }

    @Test
    fun `duplicate permissions are deduplicated in NonEmptySet`() {
        val source = RoleR(permissions = listOf(PermissionR("read"), PermissionR("read")))
        val domain = source.toRoleD()
        domain.permissions.size shouldBe 1
        assertTrue(domain.permissions.any { it.name == "read" })
    }

    @Test
    fun `empty permissions list fails with EmptyCollection carrying the field path`() {
        val exception =
            assertFailsWith<MappingException.EmptyCollection> {
                RoleR(permissions = emptyList()).toRoleD()
            }
        exception.path shouldBe "permissions"
        exception.detail shouldBe "NonEmptySet source was empty"
    }
}
