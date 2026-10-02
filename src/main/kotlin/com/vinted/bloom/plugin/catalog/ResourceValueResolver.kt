package com.vinted.bloom.plugin.catalog

import com.vinted.bloom.plugin.model.FoundationKind
import java.awt.Color

internal data class ResourceValue(
    val type: String,
    val name: String,
    val rawValue: String,
)

internal class ResourceValueResolver(
    private val resources: Map<String, ResourceValue>,
) {
    fun resolve(type: String, name: String, visited: MutableSet<String> = mutableSetOf()): String? {
        val key = "$type:$name"
        if (!visited.add(key)) return null
        val raw = resources[key]?.rawValue?.trim() ?: return null
        val alias = RESOURCE_REFERENCE.matchEntire(raw)
        return if (alias != null) {
            resolve(alias.groupValues[1], alias.groupValues[2], visited)
        } else {
            raw
        }
    }

    fun color(type: String, name: String): Color? {
        val value = resolve(type, name) ?: return null
        return parseColor(value)
    }

    fun kindFor(type: String): FoundationKind? = when (type) {
        "color" -> FoundationKind.COLOR
        "dimen" -> FoundationKind.DIMENSION
        "drawable" -> FoundationKind.DRAWABLE
        else -> null
    }

    companion object {
        private val RESOURCE_REFERENCE = Regex("@([a-zA-Z_]+)/(\\w+)")

        fun parseColor(raw: String): Color? {
            val value = raw.trim().removePrefix("#")
            val argb = when (value.length) {
                3 -> "FF" + value.map { "$it$it" }.joinToString("")
                4 -> value.map { "$it$it" }.joinToString("")
                6 -> "FF$value"
                8 -> value
                else -> return null
            }.toLongOrNull(16) ?: return null
            return Color(argb.toInt(), true)
        }
    }
}
