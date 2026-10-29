package io.horizontalsystems.walletkit.core.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.horizontalsystems.marketkit.models.TokenQuery

internal object CustomTokenCatalog {
    fun decode(json: String?): List<CustomTokenRecord> {
        if (json == null) return emptyList()
        val entries = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return emptyList()
        return entries.mapNotNull { entry ->
            runCatching {
                val value = entry.asJsonObject
                val id = value.field("tokenQueryId", "a").stringValue()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (TokenQuery.fromId(id) == null) return@mapNotNull null
                val name = value.field("name", "b").stringValue()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val code = value.field("code", "c").stringValue()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val precision = value.field("decimals", "d") ?: return@mapNotNull null
                if (!precision.isJsonPrimitive || !precision.asJsonPrimitive.isNumber) return@mapNotNull null
                val decimals = precision.asString.toIntOrNull()?.takeIf { it in 0..255 } ?: return@mapNotNull null
                CustomTokenRecord(id, name, code, decimals, value.field("image", "e").stringValue())
            }.getOrNull()
        }
    }

    // Explicit JSON keys stay stable when R8 renames the record's Kotlin fields.
    fun encode(records: List<CustomTokenRecord>): String = JsonArray().apply {
        records.forEach { record ->
            add(JsonObject().apply {
                addProperty("tokenQueryId", record.tokenQueryId)
                addProperty("name", record.name)
                addProperty("code", record.code)
                addProperty("decimals", record.decimals)
                record.image?.let { addProperty("image", it) }
            })
        }
    }.toString()

    // Verified old release APKs stored these a/b/c/d/e aliases; never guess between conflicting identities.
    private fun JsonObject.field(name: String, legacyName: String): JsonElement? {
        val canonical = get(name)
        val legacy = get(legacyName)
        require(canonical == null || legacy == null || canonical == legacy)
        return canonical ?: legacy
    }

    private fun JsonElement?.stringValue(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
