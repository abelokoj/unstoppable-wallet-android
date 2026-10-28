package io.horizontalsystems.walletkit.core.adapters

import com.google.gson.JsonElement
import java.math.BigInteger

/** NEP-141 U128 values are decimal JSON strings, never an indexer's fallback amount. */
internal fun verifiedNearTokenBalance(response: JsonElement): BigInteger {
    require(response.isJsonPrimitive && response.asJsonPrimitive.isString) { "Invalid NEAR token balance" }
    val text = response.asString
    require(text.matches(Regex("0|[1-9][0-9]{0,38}"))) { "Invalid NEAR token balance" }
    return text.toBigInteger().also {
        require(it.bitLength() <= 128) { "NEAR token balance exceeds U128" }
    }
}
