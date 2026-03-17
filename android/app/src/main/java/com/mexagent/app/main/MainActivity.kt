package com.mexagent.app.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mexagent.app.databinding.ActivityMainBinding
import com.mexagent.app.logs.LogActivity
import com.mexagent.app.overlay.OverlayService
import com.mexagent.app.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            Toast.makeText(
                this,
                "Overlay permission is required for the floating control panel",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeUiState()
        setupButtons()
        viewModel.checkStatus()
    }

    private fun observeUiState() = lifecycleScope.launch {
        viewModel.uiState.collectLatest { state ->
            binding.tvBackendUrl.text = "Backend: ${state.backendUrl}"
            binding.tvStatus.text     = "Status: ${state.agentStatus}"
            binding.tvConnectionBadge.text = if (state.isConnected) "CONNECTED" else "OFFLINE"
            binding.tvConnectionBadge.setBackgroundResource(
                if (state.isConnected) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            )
            state.currentScreen?.let { binding.tvCurrentScreen.text = "Screen: $it" }
            if (state.actionsExecuted > 0) {
                binding.tvActionsCount.text = "Actions: ${state.actionsExecuted}"
            }
            binding.btnCheckStatus.isEnabled = !state.isLoading
            state.errorMessage?.let {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        binding.btnShowOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnCheckStatus.setOnClickListener { viewModel.checkStatus() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnViewLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
            )
        }
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(
            this,
            "Floating control started. You can now switch to the app under test.",
            Toast.LENGTH_LONG
        ).show()
    }
}
