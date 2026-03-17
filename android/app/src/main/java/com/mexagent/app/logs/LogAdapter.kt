package com.mexagent.app.logs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mexagent.app.databinding.ItemLogEntryBinding

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogViewHolder(private val binding: ItemLogEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntry) {
            binding.tvLevel.text    = "[${entry.level}]"
            binding.tvMessage.text  = entry.message
            binding.tvTimestamp.text = entry.timestamp.takeLast(12)

            val color = when (entry.level) {
                "PASS"  -> Color.parseColor("#4CAF50")
                "FAIL"  -> Color.parseColor("#F44336")
                "ACT"   -> Color.parseColor("#2196F3")
                "ERROR" -> Color.parseColor("#FF5722")
                else    -> Color.parseColor("#9E9E9E")
            }
            binding.tvLevel.setTextColor(color)
            binding.viewIndicator.setBackgroundColor(color)
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(old: LogEntry, new: LogEntry) = old.id == new.id
        override fun areContentsTheSame(old: LogEntry, new: LogEntry) = old == new
    }
}
