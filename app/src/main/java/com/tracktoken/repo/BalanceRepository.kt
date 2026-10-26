package com.tracktoken.repo

import android.content.Context
import com.tracktoken.data.Store
import com.tracktoken.model.ProviderStatus
import com.tracktoken.provider.ProviderRegistry
import com.tracktoken.provider.failure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BalanceRepository {

    /**
     * Probes every stored key, caches the results for offline widgets and returns them.
     * One failing key never blocks the others.
     */
    suspend fun refreshAll(context: Context): List<ProviderStatus> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val keys = Store.loadKeys(app)

        if (keys.isEmpty()) {
            Store.saveStatuses(app, emptyList())
            return@withContext emptyList()
        }

        val statuses = keys.map { entry ->
            try {
                ProviderRegistry.clientFor(entry.kind).fetch(entry)
            } catch (t: Throwable) {
                failure(entry, t)
            }
        }

        Store.saveStatuses(app, statuses)
        statuses
    }
}
