package com.mexagent.app.overlay

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.mexagent.app.MExAgentApp
import com.mexagent.app.R
import com.mexagent.app.agent.AgentController
import com.mexagent.app.agent.AgentState
import com.mexagent.app.logs.LogActivity
import com.mexagent.app.network.ApiClient
import com.mexagent.app.settings.SettingsActivity
import com.mexagent.app.settings.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var highlightView: HighlightOverlayView
    private lateinit var agentController: AgentController
    private lateinit var dataStore: SettingsDataStore
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView

    // Animation ticker at ~30fps
    private val animHandler = Handler(Looper.getMainLooper())
    private val animTick = object : Runnable {
        override fun run() {
            highlightView.tick()
            animHandler.postDelayed(this, 33)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager   = getSystemService(WINDOW_SERVICE) as WindowManager
        agentController = AgentController(this)
        dataStore       = SettingsDataStore(this)
        startForeground(MExAgentApp.OVERLAY_NOTIFICATION_ID, buildNotification())
        createHighlightOverlay()
        createOverlayView()
        observeAgentState()
        animHandler.post(animTick)
    }

    // ── Full-screen transparent highlight layer ───────────────────────────────

    private fun createHighlightOverlay() {
        highlightView = HighlightOverlayView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(highlightView, params)
    }

    // ── Control panel ─────────────────────────────────────────────────────────

    private fun createOverlayView() {
        // ContextThemeWrapper required — Service has no UI theme, causing inflate crashes
        val themedCtx = android.view.ContextThemeWrapper(this, android.R.style.Theme_Material_Light)
        overlayView = LayoutInflater.from(themedCtx).inflate(R.layout.overlay_panel, null)
        btnStart = overlayView.findViewById<Button>(R.id.btnOverlayStart)
        btnStop  = overlayView.findViewById<Button>(R.id.btnOverlayStop)
        tvStatus = overlayView.findViewById(R.id.tvOverlayStatus)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16; y = 220
        }

        makeDraggable(overlayView, params)
        windowManager.addView(overlayView, params)

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener  { onStopClicked() }
        overlayView.findViewById<Button>(R.id.btnOverlayLogs)?.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        overlayView.findViewById<Button>(R.id.btnOverlaySettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initX = 0; var initY = 0
        var touchX = 0f; var touchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = event.rawX; touchY = event.rawY; false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - touchX).toInt()
                    params.y = initY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params); true
                }
                else -> false
            }
        }
    }

    // ── Agent actions ─────────────────────────────────────────────────────────

    private fun onStartClicked() = serviceScope.launch {
        val depth  = dataStore.depth.first()
        val aiMode = dataStore.aiMode.first()
        agentController.start(depth, aiMode)
        startHighlightPolling()
    }

    private fun onStopClicked() = serviceScope.launch {
        agentController.stop()
        highlightView.update(null)
    }

    // ── Highlight polling (300ms) ─────────────────────────────────────────────

    private var highlightJob: Job? = null

    private fun startHighlightPolling() {
        highlightJob?.cancel()
        highlightJob = serviceScope.launch(Dispatchers.IO) {
            val baseUrl = dataStore.backendUrl.first()
            val api     = ApiClient.getService(baseUrl)
            while (isActive) {
                try {
                    val resp = api.getHighlight()
                    if (resp.isSuccessful) {
                        val h = resp.body()
                        withContext(Dispatchers.Main) {
                            if (h != null && h.active && h.width > 0 && h.height > 0) {
                                highlightView.update(
                                    HighlightOverlayView.HighlightData(
                                        x      = h.x,
                                        y      = h.y,
                                        width  = h.width,
                                        height = h.height,
                                        label  = h.label,
                                        type   = h.type,
                                        status = h.status
                                    )
                                )
                            } else {
                                highlightView.update(null)
                            }
                        }
                    }
                } catch (_: Exception) { }
                delay(300)
            }
        }
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeAgentState() = serviceScope.launch {
        agentController.state.collect { state ->
            when (state) {
                is AgentState.Idle -> {
                    tvStatus.text = "Idle"
                    btnStart.isEnabled = true; btnStop.isEnabled = false
                    highlightJob?.cancel(); highlightView.update(null)
                }
                is AgentState.Starting -> {
                    tvStatus.text = "Starting"
                    btnStart.isEnabled = false; btnStop.isEnabled = false
                }
                is AgentState.Running -> {
                    tvStatus.text = "Running"
                    btnStart.isEnabled = false; btnStop.isEnabled = true
                }
                is AgentState.Stopping -> {
                    tvStatus.text = "Stopping"
                    btnStart.isEnabled = false; btnStop.isEnabled = false
                }
                is AgentState.Error -> {
                    tvStatus.text = "Error"
                    btnStart.isEnabled = true; btnStop.isEnabled = false
                    highlightJob?.cancel(); highlightView.update(null)
                }
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, MExAgentApp.OVERLAY_CHANNEL_ID)
            .setContentTitle("MExAgent Active")
            .setContentText("Floating overlay is running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        animHandler.removeCallbacks(animTick)
        serviceScope.cancel()
        if (::highlightView.isInitialized) windowManager.removeView(highlightView)
        if (::overlayView.isInitialized)   windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
