package com.mexagent.app.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mexagent.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"

        // Configure NumberPicker range
        binding.npDepth.minValue = 1
        binding.npDepth.maxValue = 10

        observeSettings()
        setupSaveButton()
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            viewModel.backendUrl.collectLatest { url ->
                if (binding.etBackendUrl.text.toString() != url) {
                    binding.etBackendUrl.setText(url)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.depth.collectLatest { d ->
                binding.npDepth.value = d
            }
        }
        lifecycleScope.launch {
            viewModel.aiMode.collectLatest { enabled ->
                binding.switchAiMode.isChecked = enabled
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val url = binding.etBackendUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.tilBackendUrl.error = "URL cannot be empty"
                return@setOnClickListener
            }
            binding.tilBackendUrl.error = null
            viewModel.setBackendUrl(url)
            viewModel.setDepth(binding.npDepth.value)
            viewModel.setAiMode(binding.switchAiMode.isChecked)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
