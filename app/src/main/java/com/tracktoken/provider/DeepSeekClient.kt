package com.tracktoken.provider

import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.Fmt
import com.tracktoken.model.ProviderStatus
import com.tracktoken.model.QuotaRow
import org.json.JSONObject

/**
 * DeepSeek: GET https://api.deepseek.com/user/balance
 *   { "is_available": true,
 *     "balance_infos": [ { "currency":"USD", "total_balance":"110.00",
 *                          "granted_balance":"10.00", "topped_up_balance":"100.00" } ] }
 * Amounts are decimal strings. We prefer the USD row, otherwise the first one.
 */
object DeepSeekClient : ProviderClient {

    private const val BALANCE_URL = "https://api.deepseek.com/user/balance"

    override fun fetch(entry: ApiKeyEntry): ProviderStatus {
        val reply = Http.get(BALANCE_URL, entry.apiKey)
        if (reply.code != 200) return failure(entry, reply.code)

        val root = runCatching { JSONObject(reply.body) }.getOrNull()
            ?: return failure(entry, IllegalStateException("BAD JSON FROM DEEPSEEK"))

        val infos = root.optJSONArray("balance_infos")
        val entries = (0 until (infos?.length() ?: 0)).mapNotNull { i ->
            infos?.optJSONObject(i)?.let { o ->
                Triple(
                    o.optString("currency", "USD"),
                    o.optString("total_balance").trim().toDoubleOrNull(),
                    o.optString("granted_balance").trim().toDoubleOrNull() to
                        o.optString("topped_up_balance").trim().toDoubleOrNull()
                )
            }
        }.filter { it.second != null }

        val chosen = entries.firstOrNull { it.first.equals("USD", ignoreCase = true) }
            ?: entries.firstOrNull()
            ?: return failure(entry, IllegalStateException("NO BALANCE ROWS"))

        val rows = mutableListOf<QuotaRow>()
        entries.filter { it.first != chosen.first }.forEach { other ->
            rows += QuotaRow(other.first, Fmt.amount(other.second, other.first))
        }
        chosen.third.first?.takeIf { it > 0 }?.let {
            rows += QuotaRow("GRANTED", Fmt.amount(it, chosen.first))
        }
        chosen.third.second?.takeIf { it > 0 }?.let {
            rows += QuotaRow("TOPPED UP", Fmt.amount(it, chosen.first))
        }

        val available = root.optBoolean("is_available", true)

        return ProviderStatus(
            entryId = entry.id,
            provider = "DeepSeek",
            label = entry.label,
            ok = true,
            remaining = chosen.second,
            spent = null,
            limit = null,
            currency = chosen.first,
            rows = rows,
            message = if (available) null else "BALANCE UNAVAILABLE",
            fetchedAt = System.currentTimeMillis()
        )
    }
}
