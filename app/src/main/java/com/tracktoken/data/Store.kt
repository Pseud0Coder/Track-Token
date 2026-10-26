package com.tracktoken.data

import android.content.Context
import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.ProviderKind
import com.tracktoken.model.ProviderStatus
import com.tracktoken.model.QuotaRow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, encrypted-at-rest-by-Android-keystore-attributes storage of the keys themselves
 * and of the last successful probe of every key (what the widgets read offline).
 */
object Store {

    private const val PREF_KEYS = "tracktoken_keys"
    private const val PREF_STATUS = "tracktoken_status"
    private const val FIELD_ENTRIES = "entries"
    private const val FIELD_STATUS = "status"
    private const val FIELD_SYNC = "sync_at"

    // ---------------------------------------------------------------- keys

    fun loadKeys(context: Context): List<ApiKeyEntry> = prefs(context, PREF_KEYS).let { p ->
        val raw = p.getString(FIELD_ENTRIES, null) ?: return emptyList()
        runCatching { decodeKeys(raw) }.getOrDefault(emptyList())
    }

    fun saveKeys(context: Context, entries: List<ApiKeyEntry>) {
        prefs(context, PREF_KEYS).edit().putString(FIELD_ENTRIES, encodeKeys(entries)).apply()
    }

    fun upsertKey(context: Context, entry: ApiKeyEntry) {
        val list = loadKeys(context).toMutableList()
        val index = list.indexOfFirst { it.id == entry.id }
        if (index >= 0) list[index] = entry else list.add(entry)
        saveKeys(context, list)
    }

    fun deleteKey(context: Context, id: String) {
        saveKeys(context, loadKeys(context).filterNot { it.id == id })
        saveStatuses(context, loadStatuses(context).filterNot { it.entryId == id })
    }

    // -------------------------------------------------------------- status

    fun loadStatuses(context: Context): List<ProviderStatus> = prefs(context, PREF_STATUS).let { p ->
        val raw = p.getString(FIELD_STATUS, null) ?: return emptyList()
        runCatching { decodeStatuses(raw) }.getOrDefault(emptyList())
    }

    fun saveStatuses(context: Context, statuses: List<ProviderStatus>) {
        prefs(context, PREF_STATUS).edit()
            .putString(FIELD_STATUS, encodeStatuses(statuses))
            .putLong(FIELD_SYNC, if (statuses.isEmpty()) 0L else statuses.maxOf { it.fetchedAt })
            .apply()
    }

    fun lastSync(context: Context): Long = prefs(context, PREF_STATUS).getLong(FIELD_SYNC, 0L)

    private fun prefs(context: Context, name: String) =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    // --------------------------------------------------------------- json

    private fun encodeKeys(entries: List<ApiKeyEntry>): String {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("kind", e.kind.name)
                put("label", e.label)
                put("apiKey", e.apiKey)
                put("endpoint", e.endpoint)
                put("remainingPath", e.remainingPath)
                put("spentPath", e.spentPath)
                put("tokensUsedPath", e.tokensUsedPath)
                put("tokensLimitPath", e.tokensLimitPath)
                put("currency", e.currency)
                put("createdAt", e.createdAt)
            })
        }
        return array.toString()
    }

    private fun decodeKeys(raw: String): List<ApiKeyEntry> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val kindName = o.optString("kind", ProviderKind.OPENROUTER.name)
            ApiKeyEntry(
                id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                kind = runCatching { ProviderKind.valueOf(kindName) }.getOrDefault(ProviderKind.OPENROUTER),
                label = o.optString("label"),
                apiKey = o.optString("apiKey"),
                endpoint = o.optString("endpoint"),
                remainingPath = o.optString("remainingPath"),
                spentPath = o.optString("spentPath"),
                tokensUsedPath = o.optString("tokensUsedPath"),
                tokensLimitPath = o.optString("tokensLimitPath"),
                currency = o.optString("currency"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun encodeStatuses(statuses: List<ProviderStatus>): String {
        val array = JSONArray()
        statuses.forEach { s ->
            array.put(JSONObject().apply {
                put("entryId", s.entryId)
                put("provider", s.provider)
                put("label", s.label)
                put("ok", s.ok)
                putOpt("remaining", s.remaining)
                putOpt("spent", s.spent)
                putOpt("limit", s.limit)
                putOpt("tokensUsed", s.tokensUsed)
                putOpt("tokensLimit", s.tokensLimit)
                put("currency", s.currency)
                put("message", s.message ?: JSONObject.NULL)
                put("fetchedAt", s.fetchedAt)
                put("rows", JSONArray().apply {
                    s.rows.forEach { r ->
                        put(JSONObject().apply {
                            put("label", r.label)
                            put("value", r.value)
                        })
                    }
                })
            })
        }
        return array.toString()
    }

    private fun decodeStatuses(raw: String): List<ProviderStatus> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            ProviderStatus(
                entryId = o.optString("entryId"),
                provider = o.optString("provider"),
                label = o.optString("label"),
                ok = o.optBoolean("ok", false),
                remaining = o.optDoubleOrNull("remaining"),
                spent = o.optDoubleOrNull("spent"),
                limit = o.optDoubleOrNull("limit"),
                tokensUsed = o.optLongOrNull("tokensUsed"),
                tokensLimit = o.optLongOrNull("tokensLimit"),
                currency = o.optString("currency"),
                rows = o.optJSONArray("rows")?.let { arr ->
                    (0 until arr.length()).mapNotNull { j ->
                        val r = arr.optJSONObject(j) ?: return@mapNotNull null
                        QuotaRow(r.optString("label"), r.optString("value"))
                    }
                } ?: emptyList(),
                message = if (o.isNull("message")) null else o.optString("message"),
                fetchedAt = o.optLong("fetchedAt", 0L)
            )
        }
    }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)
