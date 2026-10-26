package com.tracktoken.provider

import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.ProviderStatus
import com.tracktoken.model.QuotaRow
import org.json.JSONObject
import java.util.Locale

/**
 * OpenRouter exposes two useful reads:
 *   GET /api/v1/key     -> what THIS key may still spend (works with the key itself)
 *   GET /api/v1/credits -> account credits (requires a management key)
 * We try the key first, then fall back to account credits.
 */
object OpenRouterClient : ProviderClient {

    private const val KEY_URL = "https://openrouter.ai/api/v1/key"
    private const val KEY_URL_LEGACY = "https://openrouter.ai/api/v1/auth/key"
    private const val CREDITS_URL = "https://openrouter.ai/api/v1/credits"
    private const val CURRENCY = "USD"

    override fun fetch(entry: ApiKeyEntry): ProviderStatus {
        val rows = mutableListOf<QuotaRow>()
        var remaining: Double? = null
        var spent: Double? = null
        var keyCode = 0
        var keyOk = false
        var keyLabel: String? = null

        val keyReply = Http.get(KEY_URL, entry.apiKey)
            .let { if (it.code == 404) Http.get(KEY_URL_LEGACY, entry.apiKey) else it }
        keyCode = keyReply.code

        if (keyReply.code == 200) {
            val data = runCatching { JSONObject(keyReply.body).getJSONObject("data") }.getOrNull()
            if (data != null) {
                keyOk = true
                keyLabel = data.optString("label").ifBlank { null }

                val limit = data.optDoubleOrNull("limit")
                val limitRemaining = data.optDoubleOrNull("limit_remaining")
                val usage = data.optDoubleOrNull("usage")

                if (limit != null && limitRemaining != null) {
                    remaining = limitRemaining
                    spent = usage ?: (limit - limitRemaining)
                } else {
                    val budget = data.optJSONArray("budgets")?.optJSONObject(0)
                    val budgetLimit = budget?.optDoubleOrNull("limit_usd")
                    val budgetRemaining = budget?.optDoubleOrNull("remaining_usd")
                    if (budgetLimit != null && budgetRemaining != null) {
                        remaining = budgetRemaining
                        spent = budget?.optDoubleOrNull("spend_usd") ?: (budgetLimit - budgetRemaining)
                    }
                }

                data.optDoubleOrNull("usage_daily")?.let {
                    rows += QuotaRow("USED TODAY", usd(it))
                }
                data.optDoubleOrNull("usage_monthly")?.let {
                    rows += QuotaRow("USED 30D", usd(it))
                }
                data.optJSONObject("free_model_daily_requests")?.optLongOrNull("remaining")?.let {
                    rows += QuotaRow("FREE REQ LEFT", it.toString())
                }
                rows += QuotaRow("KEY", keyLabel ?: entry.keyTail)
            }
        }

        var creditsCode = 0
        if (remaining == null) {
            val credits = Http.get(CREDITS_URL, entry.apiKey)
            creditsCode = credits.code
            if (credits.code == 200) {
                val data = runCatching { JSONObject(credits.body).getJSONObject("data") }.getOrNull()
                val total = data?.optDoubleOrNull("total_credits")
                val usage = data?.optDoubleOrNull("total_usage")
                if (total != null && usage != null) {
                    remaining = total - usage
                    spent = usage
                }
            }
        }

        if (remaining == null) {
            if (keyOk) {
                return ProviderStatus(
                    entryId = entry.id,
                    provider = "OpenRouter",
                    label = entry.label,
                    ok = true,
                    currency = CURRENCY,
                    rows = rows,
                    message = "UNLIMITED KEY · ADD MGMT KEY FOR ACCOUNT BALANCE",
                    fetchedAt = System.currentTimeMillis()
                )
            }
            return failure(entry, if (creditsCode != 0) creditsCode else keyCode)
        }

        return ProviderStatus(
            entryId = entry.id,
            provider = "OpenRouter",
            label = entry.label,
            ok = true,
            remaining = remaining,
            spent = spent,
            limit = if (spent != null) remaining + spent else null,
            currency = CURRENCY,
            rows = rows,
            message = null,
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun usd(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)
}
