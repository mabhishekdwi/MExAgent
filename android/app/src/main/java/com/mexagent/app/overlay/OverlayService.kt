package com.mexagent.app.overlay

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.mexagent.app.MExAgentApp
import com.mexagent.app.R
import com.mexagent.app.agent.AgentController
import com.mexagent.app.agent.AgentState
import com.mexagent.app.logs.LogActivity
import com.mexagent.app.settings.SettingsActivity
import com.mexagent.app.settings.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var agentController: AgentController
    private lateinit var dataStore: SettingsDataStore
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var btnStart: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var tvStatus: TextView

    override fun onCreate() {
        super.onCreate()
        windowManager    = getSystemService(WINDOW_SERVICE) as WindowManager
        agentController  = AgentController(this)
        dataStore        = SettingsDataStore(this)
        startForeground(MExAgentApp.OVERLAY_NOTIFICATION_ID, buildNotification())
        createOverlayView()
        observeAgentState()
    }

    private fun createOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        btnStart  = overlayView.findViewById(R.id.btnOverlayStart)
        btnStop   = overlayView.findViewById(R.id.btnOverlayStop)
        tvStatus  = overlayView.findViewById(R.id.tvOverlayStatus)

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
        overlayView.findViewById<ImageButton>(R.id.btnOverlayLogs)?.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        overlayView.findViewById<ImageButton>(R.id.btnOverlaySettings)?.setOnClickListener {
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

    private fun onStartClicked() = serviceScope.launch {
        val depth  = dataStore.depth.first()
        val aiMode = dataStore.aiMode.first()
        agentController.start(depth, aiMode)
    }

    private fun onStopClicked() = serviceScope.launch {
        agentController.stop()
    }

    private fun observeAgentState() = serviceScope.launch {
        agentController.state.collect { state ->
            when (state) {
                is AgentState.Idle     -> { tvStatus.text = "Idle";     btnStart.isEnabled = true;  btnStop.isEnabled = false }
                is AgentState.Starting -> { tvStatus.text = "Starting"; btnStart.isEnabled = false; btnStop.isEnabled = false }
                is AgentState.Running  -> { tvStatus.text = "Running";  btnStart.isEnabled = false; btnStop.isEnabled = true  }
                is AgentState.Stopping -> { tvStatus.text = "Stopping"; btnStart.isEnabled = false; btnStop.isEnabled = false }
                is AgentState.Error    -> { tvStatus.text = "Error";    btnStart.isEnabled = true;  btnStop.isEnabled = false }
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
        serviceScope.cancel()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
