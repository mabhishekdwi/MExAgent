package com.mexagent.app.settings

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mexagent.app.databinding.ActivitySettingsBinding
import com.mexagent.app.network.ApiClient
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

        binding.npDepth.minValue = 1
        binding.npDepth.maxValue = 10

        observeSettings()
        setupSaveButton()
        setupConnectionTest()
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
        lifecycleScope.launch {
            viewModel.speedMs.collectLatest { ms ->
                when {
                    ms <= 1000 -> binding.rgSpeed.check(binding.rbFast.id)
                    ms >= 3500 -> binding.rgSpeed.check(binding.rbSlow.id)
                    else       -> binding.rgSpeed.check(binding.rbMedium.id)
                }
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
            val speedMs = when (binding.rgSpeed.checkedRadioButtonId) {
                binding.rbFast.id -> 800
                binding.rbSlow.id -> 4000
                else              -> 2000
            }
            viewModel.setSpeedMs(speedMs)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupConnectionTest() {
        binding.btnTestConnection.setOnClickListener {
            val url = binding.etBackendUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Enter backend URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnTestConnection.isEnabled = false
            binding.btnTestConnection.text = "Testing..."
            binding.llConnectionResult.visibility = View.VISIBLE

            // Reset dots to grey
            val grey = Color.parseColor("#9E9E9E")
            binding.tvBackendDot.setBackgroundColor(grey)
            binding.tvAppiumDot.setBackgroundColor(grey)
            binding.tvDeviceDot.setBackgroundColor(grey)
            binding.tvBackendStatus.text = "Backend: connecting..."
            binding.tvAppiumStatus.text  = "Appium: waiting..."
            binding.tvDeviceStatus.text  = "Device: waiting..."

            lifecycleScope.launch {
                try {
                    val api  = ApiClient.getService(url)
                    val resp = api.connectionCheck()

                    if (resp.isSuccessful && resp.body() != null) {
                        val result = resp.body()!!
                        val green  = Color.parseColor("#4CAF50")
                        val red    = Color.parseColor("#F44336")

                        // Backend
                        binding.tvBackendDot.setBackgroundColor(green)
                        binding.tvBackendStatus.text = "Backend: ✓ Connected ($url)"

                        // Appium
                        val appium = result.appium
                        if (appium.connected) {
                            binding.tvAppiumDot.setBackgroundColor(green)
                            binding.tvAppiumStatus.text =
                                "Appium: ✓ v${appium.version ?: "?"} at ${appium.url}"
                        } else {
                            binding.tvAppiumDot.setBackgroundColor(red)
                            binding.tvAppiumStatus.text =
                                "Appium: ✗ Not reachable — ${appium.error ?: "check server"}"
                        }

                        // Device
                        val device = result.device
                        if (!device.udid.isNullOrEmpty()) {
                            binding.tvDeviceDot.setBackgroundColor(green)
                            binding.tvDeviceStatus.text =
                                "Device: ✓ ${device.name} (Android ${device.platformVersion})"
                        } else {
                            binding.tvDeviceDot.setBackgroundColor(Color.parseColor("#FF9800"))
                            binding.tvDeviceStatus.text = "Device: no UDID configured in .env"
                        }
                    } else {
                        showBackendError("HTTP ${resp.code()}")
                    }
                } catch (e: Exception) {
                    showBackendError(e.message ?: "timeout")
                }

                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = "Test Connection"
            }
        }
    }

    private fun showBackendError(msg: String) {
        val red = Color.parseColor("#F44336")
        binding.tvBackendDot.setBackgroundColor(red)
        binding.tvBackendStatus.text  = "Backend: ✗ $msg"
        binding.tvAppiumStatus.text   = "Appium: — (backend unreachable)"
        binding.tvDeviceStatus.text   = "Device: — (backend unreachable)"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
