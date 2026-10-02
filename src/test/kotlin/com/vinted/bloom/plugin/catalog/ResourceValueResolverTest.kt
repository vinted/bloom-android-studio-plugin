package com.vinted.bloom.plugin.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceValueResolverTest {
    private val resolver = ResourceValueResolver(
        mapOf(
            "color:alias" to ResourceValue("color", "alias", "@color/base"),
            "color:base" to ResourceValue("color", "base", "#007782"),
            "dimen:alias" to ResourceValue("dimen", "alias", "@dimen/base"),
            "dimen:base" to ResourceValue("dimen", "base", "16dp"),
        ),
    )

    @Test
    fun resolvesAliasesToTheFinalColor() {
        assertEquals("#007782", resolver.resolve("color", "alias"))
        assertEquals(0xFF007782.toInt(), resolver.color("color", "alias")?.rgb)
    }

    @Test
    fun resolvesAliasesToTheFinalDimension() {
        assertEquals("16dp", resolver.resolve("dimen", "alias"))
    }

    @Test
    fun detectsCyclesAsUnresolved() {
        val cyclic = ResourceValueResolver(
            mapOf(
                "color:a" to ResourceValue("color", "a", "@color/b"),
                "color:b" to ResourceValue("color", "b", "@color/a"),
            ),
        )
        assertNull(cyclic.resolve("color", "a"))
        assertNotNull(resolver.color("color", "base"))
    }
}
