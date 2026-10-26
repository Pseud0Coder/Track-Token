package com.tracktoken.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tracktoken.R
import com.tracktoken.data.Store
import com.tracktoken.databinding.ActivityKeyEditBinding
import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.ProviderKind

class KeyEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeyEditBinding
    private var existing: ApiKeyEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityKeyEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_ID)
        existing = id?.let { wanted -> Store.loadKeys(this).firstOrNull { it.id == wanted } }

        binding.editTitle.text = getString(
            if (existing == null) R.string.edit_title_new else R.string.edit_title_existing
        )
        binding.deleteButton.visibility = if (existing == null) View.GONE else View.VISIBLE

        existing?.let { fill(it) }

        binding.providerGroup.setOnCheckedChangeListener { _, _ -> syncCustomBlock() }
        syncCustomBlock()

        binding.closeButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { save() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    private fun fill(entry: ApiKeyEntry) {
        when (entry.kind) {
            ProviderKind.OPENROUTER -> binding.providerOpenRouter.isChecked = true
            ProviderKind.DEEPSEEK -> binding.providerDeepSeek.isChecked = true
            ProviderKind.NOVITA -> binding.providerNovita.isChecked = true
            ProviderKind.CUSTOM -> binding.providerCustom.isChecked = true
        }
        binding.inputLabel.setText(entry.label)
        binding.inputKey.setText(entry.apiKey)
        binding.inputEndpoint.setText(entry.endpoint)
        binding.inputRemaining.setText(entry.remainingPath)
        binding.inputSpent.setText(entry.spentPath)
        binding.inputTokensUsed.setText(entry.tokensUsedPath)
        binding.inputTokensLimit.setText(entry.tokensLimitPath)
        binding.inputCurrency.setText(entry.currency)
    }

    private fun selectedKind(): ProviderKind = when (binding.providerGroup.checkedRadioButtonId) {
        R.id.providerDeepSeek -> ProviderKind.DEEPSEEK
        R.id.providerNovita -> ProviderKind.NOVITA
        R.id.providerCustom -> ProviderKind.CUSTOM
        else -> ProviderKind.OPENROUTER
    }

    private fun syncCustomBlock() {
        val custom = binding.providerCustom.isChecked
        binding.customHeader.visibility = if (custom) View.VISIBLE else View.GONE
        binding.customBlock.visibility = if (custom) View.VISIBLE else View.GONE
    }

    private fun text(id: Int): String =
        when (id) {
            R.id.inputLabel -> binding.inputLabel.text?.toString()?.trim().orEmpty()
            R.id.inputKey -> binding.inputKey.text?.toString()?.trim().orEmpty()
            R.id.inputEndpoint -> binding.inputEndpoint.text?.toString()?.trim().orEmpty()
            R.id.inputRemaining -> binding.inputRemaining.text?.toString()?.trim().orEmpty()
            R.id.inputSpent -> binding.inputSpent.text?.toString()?.trim().orEmpty()
            R.id.inputTokensUsed -> binding.inputTokensUsed.text?.toString()?.trim().orEmpty()
            R.id.inputTokensLimit -> binding.inputTokensLimit.text?.toString()?.trim().orEmpty()
            else -> binding.inputCurrency.text?.toString()?.trim().orEmpty()
        }

    private fun showError(message: String) {
        binding.editError.visibility = View.VISIBLE
        binding.editError.text = message
    }

    private fun save() {
        val kind = selectedKind()
        val label = text(R.id.inputLabel)
        val key = text(R.id.inputKey)

        val problem = when {
            label.isEmpty() -> getString(R.string.validation_label)
            key.isEmpty() -> getString(R.string.validation_key)
            kind == ProviderKind.CUSTOM && text(R.id.inputEndpoint).isEmpty() ->
                getString(R.string.validation_endpoint)
            kind == ProviderKind.CUSTOM && text(R.id.inputRemaining).isEmpty() ->
                getString(R.string.validation_remaining)
            else -> null
        }
        if (problem != null) {
            showError(problem)
            return
        }

        val entry = (existing ?: ApiKeyEntry()).copy(
            kind = kind,
            label = label,
            apiKey = key,
            endpoint = text(R.id.inputEndpoint),
            remainingPath = text(R.id.inputRemaining),
            spentPath = text(R.id.inputSpent),
            tokensUsedPath = text(R.id.inputTokensUsed),
            tokensLimitPath = text(R.id.inputTokensLimit),
            currency = text(R.id.inputCurrency)
        )

        Store.upsertKey(this, entry)
        revision++
        finish()
    }

    private fun confirmDelete() {
        val target = existing ?: return
        AlertDialog.Builder(this)
            .setTitle("DELETE KEY")
            .setMessage("Remove \"${target.label}\" and its cached balance?")
            .setPositiveButton(R.string.delete) { _, _ ->
                Store.deleteKey(this, target.id)
                revision++
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "com.tracktoken.KEY_ID"

        /** Bumped whenever a key changes so the list knows to re-sync. */
        @JvmField
        var revision: Int = 0
    }
}
