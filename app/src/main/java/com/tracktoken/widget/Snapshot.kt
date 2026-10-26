package com.tracktoken.widget

import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.Fmt
import com.tracktoken.model.ProviderStatus

/** One line of provider telemetry, ready to be pushed into a RemoteViews. */
data class SnapshotRow(
    val name: String,
    val value: String,
    val status: String,
    val fill: Int?,
    val ok: Boolean
)

/**
 * Everything a widget needs, pre-formatted so the renderer never touches the network
 * and never parses JSON inside the launcher process.
 */
data class Snapshot(
    val rows: List<SnapshotRow>,
    val total: String,
    val totalCurrency: String,
    val fill: Int,
    val updated: String,
    val keyCount: Int,
    val errorCount: Int,
    val tokenLine: String
) {
    val isEmpty: Boolean get() = keyCount == 0
}

object SnapshotFactory {

    fun build(keys: List<ApiKeyEntry>, statuses: List<ProviderStatus>): Snapshot {
        val byId = statuses.associateBy { it.entryId }

        val rows = keys.map { key ->
            val status = byId[key.id]
            SnapshotRow(
                name = key.label.ifBlank { key.kind.display }.uppercase().take(16),
                value = status?.let { Fmt.amount(it.remaining, it.currency) } ?: "--",
                status = when {
                    status == null -> "PENDING"
                    status.ok -> "OK"
                    else -> (status.message ?: "ERR").take(24)
                },
                fill = status?.fillPercent,
                ok = status?.ok == true
            )
        }

        val amounts = statuses.mapNotNull { s -> s.remaining?.let { it to s.currency.uppercase() } }
        val sameCurrency = amounts.isNotEmpty() && amounts.all { it.second == amounts.first().second }
        val total = when {
            amounts.isEmpty() -> "--"
            sameCurrency -> Fmt.amount(amounts.sumOf { it.first }, amounts.first().second)
            else -> "MIXED"
        }

        val fills = rows.mapNotNull { it.fill }
        val average = if (fills.isEmpty()) null else fills.average().toInt().coerceIn(0, 100)

        val tokens = statuses.firstOrNull { it.tokensUsed != null }
        val tokenLine = tokens?.tokensText?.let { " · TOK $it" } ?: ""

        val latest = statuses.maxOfOrNull { it.fetchedAt } ?: 0L

        return Snapshot(
            rows = rows,
            total = total,
            totalCurrency = if (sameCurrency && amounts.isNotEmpty()) amounts.first().second else "",
            fill = average ?: 0,
            updated = Fmt.clock(latest),
            keyCount = keys.size,
            errorCount = statuses.count { !it.ok },
            tokenLine = tokenLine
        )
    }
}
