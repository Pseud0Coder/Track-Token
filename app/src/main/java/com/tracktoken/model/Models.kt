package com.tracktoken.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Providers TrackToken knows how to interrogate for a remaining balance. */
enum class ProviderKind(val display: String) {
    OPENROUTER("OpenRouter"),
    DEEPSEEK("DeepSeek"),
    NOVITA("Novita AI"),
    CUSTOM("Custom HTTP")
}

/** One stored API key, optionally with custom endpoint/JSON-path config. */
data class ApiKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val kind: ProviderKind = ProviderKind.OPENROUTER,
    val label: String = "",
    val apiKey: String = "",
    val endpoint: String = "",
    val remainingPath: String = "",
    val spentPath: String = "",
    val tokensUsedPath: String = "",
    val tokensLimitPath: String = "",
    val currency: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isCustom: Boolean get() = kind == ProviderKind.CUSTOM

    /** A short, non-secret signature used to detect key rotation. */
    val keyTail: String get() = if (apiKey.length <= 4) "••••" else "…" + apiKey.takeLast(4)
}

/** One extra fact a provider reported, rendered as a secondary line. */
data class QuotaRow(val label: String, val value: String)

/** Result of one balance probe. Never contains the API key itself. */
data class ProviderStatus(
    val entryId: String,
    val provider: String,
    val label: String,
    val ok: Boolean,
    val remaining: Double? = null,
    val spent: Double? = null,
    val limit: Double? = null,
    val tokensUsed: Long? = null,
    val tokensLimit: Long? = null,
    val currency: String = "",
    val rows: List<QuotaRow> = emptyList(),
    val message: String? = null,
    val fetchedAt: Long = 0L
) {
    /** 0..100 of budget still available, when a budget is knowable. */
    val fillPercent: Int?
        get() {
            val rem = remaining ?: return null
            val lim = limit ?: return null
            if (lim <= 0.0) return null
            return ((rem / lim) * 100).roundToInt().coerceIn(0, 100)
        }

    val tokensText: String?
        get() {
            val used = Fmt.tokens(tokensUsed) ?: return null
            val cap = tokensLimit?.let { "/" + (Fmt.tokens(it) ?: "?") } ?: ""
            return used + cap + " tok"
        }
}

object Fmt {
    private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)

    fun symbol(currency: String): String = when (currency.trim().uppercase(Locale.US)) {
        "", "USD", "$" -> "$"
        "CNY", "RMB" -> "¥"
        "JPY" -> "¥"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> ""
    }

    fun amount(value: Double?, currency: String): String {
        val v = value ?: return "--"
        val num = String.format(Locale.US, "%,.2f", v)
        val sym = symbol(currency)
        if (sym.isNotEmpty()) return sym + num
        val cur = currency.trim().uppercase(Locale.US)
        return if (cur.isEmpty()) num else "$num $cur"
    }

    fun tokens(value: Long?): String? {
        val v = value ?: return null
        val d = v.toDouble()
        return when {
            v >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", d / 1_000_000_000)
            v >= 1_000_000 -> String.format(Locale.US, "%.1fM", d / 1_000_000)
            v >= 1_000 -> String.format(Locale.US, "%.1fk", d / 1_000)
            else -> v.toString()
        }
    }

    fun clock(timestamp: Long): String = if (timestamp <= 0L) "--:--" else clockFmt.format(Date(timestamp))

    fun percentOf(remaining: Double?, limit: Double?): Int? {
        if (remaining == null || limit == null || limit <= 0.0) return null
        return ((remaining / limit) * 100).roundToInt().coerceIn(0, 100)
    }
}
