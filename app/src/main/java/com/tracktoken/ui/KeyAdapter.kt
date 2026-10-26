package com.tracktoken.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tracktoken.R
import com.tracktoken.databinding.ItemKeyBinding
import com.tracktoken.model.ApiKeyEntry
import com.tracktoken.model.Fmt
import com.tracktoken.model.ProviderStatus
import java.util.Locale

class KeyAdapter(
    private val onClick: (ApiKeyEntry) -> Unit
) : RecyclerView.Adapter<KeyAdapter.VH>() {

    private var keys: List<ApiKeyEntry> = emptyList()
    private var statuses: Map<String, ProviderStatus> = emptyMap()

    fun submit(entries: List<ApiKeyEntry>, results: List<ProviderStatus>) {
        keys = entries
        statuses = results.associateBy { it.entryId }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemKeyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = keys.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(keys[position])

    inner class VH(private val binding: ItemKeyBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ApiKeyEntry) {
            val context = binding.root.context
            val status = statuses[entry.id]

            val neonGreen = ContextCompat.getColor(context, R.color.neon_green)
            val neonMagenta = ContextCompat.getColor(context, R.color.neon_magenta)
            val textDim = ContextCompat.getColor(context, R.color.text_dim)
            val textBody = ContextCompat.getColor(context, R.color.text_body)

            binding.keyProvider.text = entry.kind.display.uppercase(Locale.US)
            binding.keyLabel.text = entry.label.ifBlank { entry.kind.display }

            val accent = when {
                status == null -> textDim
                status.ok -> neonGreen
                else -> neonMagenta
            }
            binding.keyBadge.backgroundTintList = ColorStateList.valueOf(accent)
            binding.keyValue.setTextColor(accent)
            binding.keyValue.text = status?.let { Fmt.amount(it.remaining, it.currency) } ?: "--"

            binding.keyBar.progress = status?.fillPercent ?: 0

            binding.keyMetaLeft.text = when {
                status?.spent != null -> "SPENT " + Fmt.amount(status.spent, status.currency)
                status?.tokensText != null -> status.tokensText!!
                status != null && status.ok -> "NO SPEND DATA"
                else -> "AWAITING SYNC"
            }

            binding.keyMetaRight.text =
                "SYNC " + Fmt.clock(status?.fetchedAt ?: 0L) + "  ·  " + entry.keyTail

            when {
                status == null -> showError("NOT SYNCED YET", textDim)
                !status.ok -> showError(status.message ?: "SYNC FAILED", neonMagenta)
                status.message != null -> showError(status.message, textDim)
                else -> binding.keyError.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onClick(entry) }
        }

        private fun showError(message: String, color: Int) {
            binding.keyError.visibility = android.view.View.VISIBLE
            binding.keyError.setTextColor(color)
            binding.keyError.text = message
        }
    }
}
