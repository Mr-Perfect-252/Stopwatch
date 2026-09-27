package com.apexhub.stopwatch

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.apexhub.sdk.ApexHubConfig
import com.apexhub.sdk.ApexHubUpdater
import com.opensdk.analytics.OpenAnalytics
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var updater: ApexHubUpdater

    private lateinit var elapsedText: TextView
    private lateinit var startStopButton: Button
    private lateinit var lapList: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var accumulatedMs = 0L     // time banked from previous run segments
    private var segmentStart = 0L      // SystemClock.elapsedRealtime() at last start
    private var lastLapMs = 0L
    private var lapCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            elapsedText.text = format(elapsedMs())
            if (running) handler.postDelayed(this, 33)   // ~30 fps
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ── ApexHub OTA SDK ────────────────────────────────────────────────
        updater = ApexHubUpdater(
            context = this,
            config = ApexHubConfig(publicKey = StopwatchApp.PUBLIC_KEY)
        )

        elapsedText = findViewById(R.id.elapsed)
        startStopButton = findViewById(R.id.btn_start_stop)
        lapList = findViewById(R.id.lap_list)

        startStopButton.setOnClickListener { toggleRun() }
        findViewById<Button>(R.id.btn_lap).setOnClickListener { recordLap() }
        findViewById<Button>(R.id.btn_reset).setOnClickListener { reset() }
        findViewById<Button>(R.id.btn_update).setOnClickListener {
            lifecycleScope.launch { updater.checkAndPrompt(activity = this@MainActivity) }
        }

        elapsedText.text = format(0)

        // ── apex-analytics ─────────────────────────────────────────────────
        // A named screen view (automatic screen_view tracking is also on).
        OpenAnalytics.trackScreen("stopwatch")
        OpenAnalytics.track("app_opened")
    }

    override fun onResume() {
        super.onResume()
        // Surface any crash captured on a previous run so the user can send it.
        OpenAnalytics.processPendingCrashReports()
    }

    private fun elapsedMs(): Long =
        accumulatedMs + if (running) SystemClock.elapsedRealtime() - segmentStart else 0L

    private fun toggleRun() {
        if (running) {
            accumulatedMs = elapsedMs()
            running = false
            handler.removeCallbacks(ticker)
            startStopButton.text = getString(R.string.start)
            OpenAnalytics.track(
                "stopwatch_paused",
                properties = mapOf("elapsed_ms" to accumulatedMs)
            )
        } else {
            segmentStart = SystemClock.elapsedRealtime()
            running = true
            handler.post(ticker)
            startStopButton.text = getString(R.string.pause)
            OpenAnalytics.track("stopwatch_started")
        }
    }

    private fun recordLap() {
        if (!running && accumulatedMs == 0L) return
        val total = elapsedMs()
        val lapMs = total - lastLapMs
        lastLapMs = total
        lapCount += 1

        val row = LayoutInflater.from(this)
            .inflate(android.R.layout.simple_list_item_1, lapList, false) as TextView
        row.text = getString(R.string.lap_row, lapCount, format(lapMs), format(total))
        row.setTextColor(0xFFC9D1D9.toInt())
        lapList.addView(row, 0)   // newest lap on top

        OpenAnalytics.track(
            "lap_recorded",
            properties = mapOf("lap" to lapCount, "lap_ms" to lapMs, "total_ms" to total)
        )
    }

    private fun reset() {
        running = false
        handler.removeCallbacks(ticker)
        accumulatedMs = 0L
        lastLapMs = 0L
        lapCount = 0
        lapList.removeAllViews()
        elapsedText.text = format(0)
        startStopButton.text = getString(R.string.start)
        OpenAnalytics.track("stopwatch_reset")
    }

    private fun format(ms: Long): String {
        val centis = (ms / 10) % 100
        val totalSec = ms / 1000
        val secs = totalSec % 60
        val mins = (totalSec / 60) % 60
        val hours = totalSec / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d.%02d", hours, mins, secs, centis)
        } else {
            String.format(Locale.US, "%02d:%02d.%02d", mins, secs, centis)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
    }
}
