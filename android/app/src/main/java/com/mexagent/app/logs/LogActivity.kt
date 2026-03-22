package com.mexagent.app.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mexagent.app.R
import com.mexagent.app.databinding.ActivityLogBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private val viewModel: LogViewModel by viewModels()
    private val adapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Agent Logs"

        binding.rvLogs.apply {
            layoutManager = LinearLayoutManager(this@LogActivity).also {
                it.stackFromEnd = true
            }
            adapter = this@LogActivity.adapter
        }

        lifecycleScope.launch {
            viewModel.logs.collectLatest { entries ->
                adapter.submitList(entries.toList())
            }
        }

        lifecycleScope.launch {
            viewModel.scrollToBottom.collect {
                val count = adapter.itemCount
                if (count > 0) binding.rvLogs.scrollToPosition(count - 1)
            }
        }

        viewModel.startPolling()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy -> {
                val logs = viewModel.logs.value
                if (logs.isEmpty()) {
                    Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
                } else {
                    val text = logs.joinToString("\n") { "[${it.level}] ${it.timestamp} ${it.message}" }
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MExAgent Logs", text))
                    Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_clear -> {
                viewModel.clearLogs()
                adapter.submitList(emptyList())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopPolling()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
