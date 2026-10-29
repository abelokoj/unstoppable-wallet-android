package io.horizontalsystems.walletkit.core.managers

import com.google.gson.JsonParser
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTokenCatalogTest {
    private val id = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0x1111111111111111111111111111111111111111")).id
    private val record = CustomTokenRecord(id, "Test Token", "TEST", 18, "https://example.com/token.png")
    private val canonical get() = """{"tokenQueryId":"$id","name":"Test Token","code":"TEST","decimals":18,"image":"https://example.com/token.png"}"""
    private val legacy get() = """{"a":"$id","b":"Test Token","c":"TEST","d":18,"e":"https://example.com/token.png"}"""

    @Test
    fun canonicalRecordsRoundTripWithStableKeys() {
        val encoded = CustomTokenCatalog.encode(listOf(record))
        assertEquals(setOf("tokenQueryId", "name", "code", "decimals", "image"), JsonParser.parseString(encoded).asJsonArray[0].asJsonObject.keySet())
        assertEquals(listOf(record), CustomTokenCatalog.decode(encoded))
        assertEquals(listOf(record), CustomTokenCatalog.decode("[$canonical]"))
    }

    @Test
    fun readsOldOptimizedFieldNamesAndWritesCanonicalNames() {
        val decoded = CustomTokenCatalog.decode("[$legacy]")
        assertEquals(listOf(record), decoded)
        assertEquals(JsonParser.parseString("[$canonical]"), JsonParser.parseString(CustomTokenCatalog.encode(decoded)))
    }

    @Test
    fun malformedEntriesCannotCrashOrHideValidSiblings() {
        val invalid = listOf("null", "{}", "42", "[]", canonical.replace("\"$id\"", "null"), canonical.replace(id, "invalid"), canonical.replace("\"Test Token\"", "\"\""))
        assertEquals(listOf(record), CustomTokenCatalog.decode("[${invalid.joinToString(",")},$canonical]"))
    }

    @Test
    fun requiresAnExactPresentTokenPrecision() {
        for (precision in listOf("-1", "256", "1.5", "\"18\"", "null")) {
            assertTrue(precision, CustomTokenCatalog.decode("[${canonical.replace("\"decimals\":18", "\"decimals\":$precision")}] ").isEmpty())
        }
        assertTrue(CustomTokenCatalog.decode("[${canonical.replace("\"decimals\":18,", "")}] ").isEmpty())
        for (precision in listOf(0, 255)) {
            assertEquals(listOf(record.copy(decimals = precision)), CustomTokenCatalog.decode("[${canonical.replace("\"decimals\":18", "\"decimals\":$precision")}]"))
        }
    }

    @Test
    fun optionalImagesCanBeAbsentOrNull() {
        assertEquals(listOf(record.copy(image = null)), CustomTokenCatalog.decode("[${canonical.replace(",\"image\":\"https://example.com/token.png\"", "")}]"))
        assertEquals(listOf(record.copy(image = null)), CustomTokenCatalog.decode("[${canonical.replace("\"https://example.com/token.png\"", "null")}]"))
    }

    @Test
    fun ambiguousCanonicalAndLegacyIdentityIsRejected() {
        val conflicting = canonical.dropLast(1) + ",\"a\":\"bitcoin|native\"}"
        assertTrue(CustomTokenCatalog.decode("[$conflicting]").isEmpty())
        val same = canonical.dropLast(1) + ",\"a\":\"$id\"}"
        assertEquals(listOf(record), CustomTokenCatalog.decode("[$same]"))
    }

    @Test
    fun invalidRootsReturnNoRecords() {
        for (json in listOf(null, "", "null", "{}", "42", "[broken")) {
            assertTrue(CustomTokenCatalog.decode(json).isEmpty())
        }
    }
}
