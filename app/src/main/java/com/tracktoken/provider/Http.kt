package com.tracktoken.provider

import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.ProviderStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Thin synchronous HTTP helper. All calls run on a background dispatcher. */
internal object Http {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Reply(val code: Int, val body: String)

    fun get(url: String, key: String): Reply {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", "TrackToken/1.0 (android)")
            .build()

        client.newCall(request).execute().use { response ->
            val body = runCatching { response.body?.string() }.getOrNull() ?: ""
            return Reply(response.code, body)
        }
    }

    fun statusForCode(code: Int): String = when (code) {
        401, 403 -> "REJECTED KEY · HTTP $code"
        402 -> "NO CREDITS LEFT"
        404 -> "ENDPOINT NOT FOUND"
        429 -> "RATE LIMITED"
        in 500..599 -> "PROVIDER ERROR $code"
        else -> "HTTP $code"
    }
}

/** Builds a failed status for a key that could not be probed. */
internal fun failure(entry: ApiKeyEntry, code: Int): ProviderStatus = ProviderStatus(
    entryId = entry.id,
    provider = entry.kind.display,
    label = entry.label,
    ok = false,
    currency = entry.currency,
    message = Http.statusForCode(code),
    fetchedAt = System.currentTimeMillis()
)

internal fun failure(entry: ApiKeyEntry, throwable: Throwable): ProviderStatus = ProviderStatus(
    entryId = entry.id,
    provider = entry.kind.display,
    label = entry.label,
    ok = false,
    currency = entry.currency,
    message = (throwable.message ?: "NETWORK ERROR").take(64).uppercase().replace('\n', ' '),
    fetchedAt = System.currentTimeMillis()
)

internal fun JSONObject.optDoubleOrNull(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    return optDouble(name).takeUnless { it.isNaN() }
}

internal fun JSONObject.optLongOrNull(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return optLong(name)
}

/** Dot/index path reader: "data.budgets.0.limit_remaining". */
internal object JsonPath {

    fun read(root: Any?, path: String): Any? {
        if (path.isBlank()) return null
        var current: Any? = root
        for (raw in path.split('.')) {
            val segment = raw.trim()
            if (segment.isEmpty()) continue
            current = when (current) {
                is org.json.JSONObject ->
                    if (current.has(segment)) current.get(segment) else null
                is org.json.JSONArray ->
                    segment.toIntOrNull()?.let { i -> if (i in 0 until current.length()) current.get(i) else null }
                is Map<*, *> -> current[segment]
                else -> null
            }
            if (current == null || current === org.json.JSONObject.NULL) return null
        }
        return if (current === org.json.JSONObject.NULL) null else current
    }

    fun number(root: Any?, path: String): Double? = when (val value = read(root, path)) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }

    fun whole(root: Any?, path: String): Long? = when (val value = read(root, path)) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull() ?: value.trim().toDoubleOrNull()?.toLong()
        else -> null
    }

    fun text(root: Any?, path: String): String? = when (val value = read(root, path)) {
        null -> null
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }
}
