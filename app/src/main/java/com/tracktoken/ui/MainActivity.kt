package com.tracktoken.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tracktoken.R
import com.tracktoken.data.Store
import com.tracktoken.databinding.ActivityMainBinding
import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.Fmt
import com.tracktoken.model.ProviderStatus
import com.tracktoken.repo.BalanceRepository
import com.tracktoken.widget.WidgetPush
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: KeyAdapter
    private var seenRevision = KeyEditActivity.revision

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = KeyAdapter { entry -> openEditor(entry.id) }
        binding.keyList.layoutManager = LinearLayoutManager(this)
        binding.keyList.adapter = adapter

        binding.addButton.setOnClickListener { openEditor(null) }
        binding.syncButton.setOnClickListener { refresh() }

        render(Store.loadKeys(this), Store.loadStatuses(this))
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (KeyEditActivity.revision != seenRevision) {
            seenRevision = KeyEditActivity.revision
            refresh()
        }
    }

    private fun openEditor(id: String?) {
        startActivity(Intent(this, KeyEditActivity::class.java).apply {
            if (id != null) putExtra(KeyEditActivity.EXTRA_ID, id)
        })
    }

    private fun refresh() {
        val keys = Store.loadKeys(this)
        if (keys.isEmpty()) {
            render(keys, emptyList())
            binding.syncStatus.text = "NO KEYS LINKED"
            return
        }

        binding.syncButton.isEnabled = false
        binding.syncButton.text = getString(R.string.syncing)

        lifecycleScope.launch {
            val statuses = BalanceRepository.refreshAll(this@MainActivity)
            WidgetPush.pushAll(this@MainActivity)
            render(Store.loadKeys(this@MainActivity), statuses)
            binding.syncButton.isEnabled = true
            binding.syncButton.text = getString(R.string.sync)
            binding.syncStatus.text = "LAST SYNC " + Fmt.clock(Store.lastSync(this@MainActivity))
        }
    }

    private fun render(keys: List<ApiKeyEntry>, statuses: List<ProviderStatus>) {
        adapter.submit(keys, statuses)

        binding.emptyBlock.visibility = if (keys.isEmpty()) View.VISIBLE else View.GONE
        binding.keyList.visibility = if (keys.isEmpty()) View.GONE else View.VISIBLE

        binding.statKeys.text = keys.size.toString()
        binding.statAlerts.text = statuses.count { !it.ok }.toString()
        binding.statRemaining.text = totalRemaining(statuses)
        if (statuses.isNotEmpty()) {
            binding.syncStatus.text = "LAST SYNC " + Fmt.clock(Store.lastSync(this))
        }
    }

    /** Sums remaining credit only when every key reports the same currency. */
    private fun totalRemaining(statuses: List<ProviderStatus>): String {
        val amounts = statuses.mapNotNull { s -> s.remaining?.let { it to s.currency.uppercase() } }
        if (amounts.isEmpty()) return "--"
        val currency = amounts.first().second
        return if (amounts.all { it.second == currency }) {
            Fmt.amount(amounts.sumOf { it.first }, currency)
        } else {
            "MIXED"
        }
    }
}
