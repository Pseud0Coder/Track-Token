package com.tracktoken.provider

import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.Fmt
import com.tracktoken.model.ProviderStatus
import com.tracktoken.model.QuotaRow
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Escape hatch for any provider (or gateway) that answers a GET with JSON.
 *
 * The endpoint may contain `{key}` which is replaced by the URL-encoded API key,
 * so query-string style APIs work too. Values are read with dotted JSON paths,
 * e.g.  data.remaining_credits  /  usage.tokens  /  quota.token_limit.
 */
object CustomClient : ProviderClient {

    override fun fetch(entry: ApiKeyEntry): ProviderStatus {
        val rawUrl = entry.endpoint.trim()
        require(rawUrl.isNotBlank()) { "MISSING BALANCE URL" }

        val url = if (rawUrl.contains("{key}")) {
            rawUrl.replace("{key}", URLEncoder.encode(entry.apiKey, "UTF-8"))
        } else {
            rawUrl
        }

        val reply = Http.get(url, entry.apiKey)
        if (reply.code != 200) return failure(entry, reply.code)

        val root = runCatching { JSONObject(reply.body) }.getOrNull()
            ?: return failure(entry, IllegalStateException("RESPONSE IS NOT A JSON OBJECT"))

        val remaining = JsonPath.number(root, entry.remainingPath)
        if (remaining == null) {
            return failure(entry, IllegalStateException("REMAINING PATH NOT FOUND"))
        }
        val spent = JsonPath.number(root, entry.spentPath)
        val tokensUsed = JsonPath.whole(root, entry.tokensUsedPath)
        val tokensLimit = JsonPath.whole(root, entry.tokensLimitPath)

        val rows = mutableListOf<QuotaRow>()
        JsonPath.text(root, "currency")?.let { rows += QuotaRow("CURRENCY", it) }
        if (tokensUsed != null && tokensLimit != null) {
            rows += QuotaRow("TOKENS", "${Fmt.tokens(tokensUsed)} / ${Fmt.tokens(tokensLimit)}")
        }

        return ProviderStatus(
            entryId = entry.id,
            provider = entry.label.ifBlank { "Custom" },
            label = entry.label,
            ok = true,
            remaining = remaining,
            spent = spent,
            limit = if (spent != null) remaining + spent else null,
            tokensUsed = tokensUsed,
            tokensLimit = tokensLimit,
            currency = entry.currency,
            rows = rows.filter { it.value.isNotBlank() },
            message = null,
            fetchedAt = System.currentTimeMillis()
        )
    }
}
