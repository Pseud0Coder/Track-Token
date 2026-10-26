package com.tracktoken.provider

import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.ProviderKind
import com.tracktoken.model.ProviderStatus

/** One probe: takes a stored key, returns what the provider says is left. */
interface ProviderClient {
    fun fetch(entry: ApiKeyEntry): ProviderStatus
}

object ProviderRegistry {

    private val openRouter = OpenRouterClient
    private val deepSeek = DeepSeekClient
    private val novita = NovitaClient
    private val custom = CustomClient

    fun clientFor(kind: ProviderKind): ProviderClient = when (kind) {
        ProviderKind.OPENROUTER -> openRouter
        ProviderKind.DEEPSEEK -> deepSeek
        ProviderKind.NOVITA -> novita
        ProviderKind.CUSTOM -> custom
    }

    /** Endpoint templates exposed in the "add key" screen for documentation purposes. */
    fun describe(kind: ProviderKind): String = when (kind) {
        ProviderKind.OPENROUTER -> "openrouter.ai/api/v1/key  +  /credits"
        ProviderKind.DEEPSEEK -> "api.deepseek.com/user/balance"
        ProviderKind.NOVITA -> "api.novita.ai/…/billing/balance/detail"
        ProviderKind.CUSTOM -> "any GET endpoint returning JSON"
    }
}
