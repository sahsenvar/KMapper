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
        val result = source.toRoleD()
        result.permissions.size shouldBe 2
        assertTrue(result.permissions.any { it.name == "read" })
        assertTrue(result.permissions.any { it.name == "write" })
    }

    @Test
    fun `duplicate permissions are deduplicated in NonEmptySet`() {
        val source = RoleR(permissions = listOf(PermissionR("read"), PermissionR("read")))
        val result = source.toRoleD()
        result.permissions.size shouldBe 1
        assertTrue(result.permissions.any { it.name == "read" })
    }

    @Test
    fun `empty permissions list throws EmptyCollection`() {
        assertFailsWith<MappingException.EmptyCollection> {
            RoleR(permissions = emptyList()).toRoleD()
        }
    }
}
