package com.tracktoken.provider

import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.Fmt
import com.tracktoken.model.ProviderStatus
import com.tracktoken.model.QuotaRow
import org.json.JSONObject

/**
 * Novita AI: GET https://api.novita.ai/openapi/v1/billing/balance/detail
 * Reported amounts are strings in 1/10,000 USD (10000 == $1.00).
 */
object NovitaClient : ProviderClient {

    private const val BALANCE_URL = "https://api.novita.ai/openapi/v1/billing/balance/detail"
    private const val SCALE = 10_000.0

    override fun fetch(entry: ApiKeyEntry): ProviderStatus {
        val reply = Http.get(BALANCE_URL, entry.apiKey)
        if (reply.code != 200) return failure(entry, reply.code)

        val root = runCatching { JSONObject(reply.body) }.getOrNull()
            ?: return failure(entry, IllegalStateException("BAD JSON FROM NOVITA"))

        val available = root.read("availableBalance")?.div(SCALE)
            ?: return failure(entry, IllegalStateException("NO BALANCE FIELD"))

        val rows = mutableListOf<QuotaRow>()
        root.read("creditLimit")?.div(SCALE)?.let { rows += QuotaRow("CREDIT LIMIT", Fmt.amount(it, "USD")) }
        root.read("cashBalance")?.div(SCALE)?.let { rows += QuotaRow("CASH BALANCE", Fmt.amount(it, "USD")) }
        root.read("pendingCharges")?.div(SCALE)?.let { rows += QuotaRow("PENDING", Fmt.amount(it, "USD")) }
        root.read("outstandingInvoices")?.div(SCALE)?.let { rows += QuotaRow("INVOICES", Fmt.amount(it, "USD")) }

        return ProviderStatus(
            entryId = entry.id,
            provider = "Novita AI",
            label = entry.label,
            ok = true,
            remaining = available,
            spent = null,
            limit = null,
            currency = "USD",
            rows = rows,
            message = null,
            fetchedAt = System.currentTimeMillis()
        )
    }

    /** Reads a numeric field that may arrive as a number or as a decimal string. */
    private fun JSONObject.read(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return when (val value = get(name)) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
}
