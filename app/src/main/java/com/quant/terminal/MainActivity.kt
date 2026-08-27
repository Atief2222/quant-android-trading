package com.quant.terminal

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.quant.terminal.api.ApiClient
import com.quant.terminal.api.AiScanResponse
import com.quant.terminal.api.MarketPulseResponse
import com.quant.terminal.ui.ChartBridge
import com.quant.terminal.ui.SpeedometerView
import com.quant.terminal.utils.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferenceManager
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var bottomNav: BottomNavigationView
    private val gson = Gson()

    private var activeTabId = R.id.nav_terminal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferenceManager(this)
        fragmentContainer = findViewById(R.id.fragment_container)
        bottomNav = findViewById(R.id.bottom_nav)

        setupNavigation()
        switchTab(R.id.nav_terminal)

        lifecycleScope.launch {
            ApiClient.syncActiveUrl()
            startMarketDataPolling()
        }
    }

    private fun setupNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            switchTab(item.itemId)
            true
        }
    }

    private fun switchTab(itemId: Int) {
        activeTabId = itemId
        fragmentContainer.removeAllViews()

        when (itemId) {
            R.id.nav_terminal -> {
                val view = layoutInflater.inflate(R.layout.fragment_terminal, fragmentContainer, false)
                fragmentContainer.addView(view)
                val webView = view.findViewById<android.webkit.WebView>(R.id.webview_chart)
                ChartBridge.initChart(webView)
            }
            R.id.nav_radar -> {
                val view = layoutInflater.inflate(R.layout.fragment_radar, fragmentContainer, false)
                fragmentContainer.addView(view)
                view.findViewById<SpeedometerView>(R.id.gauge_choppiness).setGaugeMode(0, "CHOPPINESS")
                view.findViewById<SpeedometerView>(R.id.gauge_mpi).setGaugeMode(1, "MACRO MPI")
            }
            R.id.nav_control -> {
                val view = layoutInflater.inflate(R.layout.fragment_control, fragmentContainer, false)
                fragmentContainer.addView(view)
                view.findViewById<Button>(R.id.btn_trigger_scan).setOnClickListener {
                    triggerAiScan()
                }
            }
            R.id.nav_settings -> {
                val view = layoutInflater.inflate(R.layout.fragment_settings, fragmentContainer, false)
                fragmentContainer.addView(view)
                setupSettingsView(view)
            }
        }
    }

    private fun setupSettingsView(view: View) {
        val tvUrl = view.findViewById<TextView>(R.id.tv_active_server_url)
        val btnSync = view.findViewById<Button>(R.id.btn_sync_url)
        val swFloating = view.findViewById<SwitchCompat>(R.id.switch_floating)

        tvUrl.text = "Server URL: ${ApiClient.activeBaseUrl.ifEmpty { "Belum terhubung" }}"
        swFloating.isChecked = prefs.isFloatingEnabled

        btnSync.setOnClickListener {
            lifecycleScope.launch {
                val ok = ApiClient.syncActiveUrl()
                tvUrl.text = "Server URL: ${ApiClient.activeBaseUrl.ifEmpty { "Gagal sinkronisasi" }}"
                Toast.makeText(this@MainActivity, if (ok) "URL Berhasil Diperbarui" else "Gagal Sinkron", Toast.LENGTH_SHORT).show()
            }
        }

        swFloating.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkOverlayPermissionAndStart()
            } else {
                prefs.isFloatingEnabled = false
                stopService(Intent(this, FloatingService::class.java))
            }
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            prefs.isFloatingEnabled = true
            startService(Intent(this, FloatingService::class.java))
        }
    }

    private fun startMarketDataPolling() {
        lifecycleScope.launch {
            while (true) {
                try {
                    val resStr = ApiClient.getData("/api/market-pulse")
                    if (!resStr.isNullOrEmpty()) {
                        val data = gson.fromJson(resStr, MarketPulseResponse::class.java)
                        updateUiData(data)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }

    private fun updateUiData(data: MarketPulseResponse) {
        runOnUiThread {
            when (activeTabId) {
                R.id.nav_terminal -> {
                    val tvPrice = findViewById<TextView>(R.id.tv_running_price)
                    tvPrice?.text = String.format("%.2f", data.analysis?.m5Indicators?.currentPrice ?: data.liveTick?.bid ?: 0.0)
                    val webView = findViewById<android.webkit.WebView>(R.id.webview_chart)
                    if (webView != null && !data.candles.isNullOrEmpty()) {
                        ChartBridge.renderCandles(webView, data.candles)
                    }
                }
                R.id.nav_radar -> {
                    val gChop = findViewById<SpeedometerView>(R.id.gauge_choppiness)
                    val gMpi = findViewById<SpeedometerView>(R.id.gauge_mpi)
                    val tvCvd = findViewById<TextView>(R.id.tv_cvd_status)
                    val tvRsi = findViewById<TextView>(R.id.tv_val_rsi)
                    val tvAtr = findViewById<TextView>(R.id.tv_val_atr)

                    val chopVal = data.analysis?.marketRegime?.choppinessIndex ?: 50.0
                    val mpiVal = data.analysis?.macroIntel?.mpi ?: 0.0

                    gChop?.setValue(chopVal.toFloat())
                    gMpi?.setValue(mpiVal.toFloat())

                    tvCvd?.text = data.analysis?.cvdOrderFlow?.absorptionStatus ?: "NORMAL"
                    tvRsi?.text = "RSI M5: ${String.format("%.1f", data.analysis?.m5Indicators?.rsi ?: 50.0)}"
                    tvAtr?.text = "ATR: $${String.format("%.2f", data.analysis?.m5Indicators?.atr ?: 0.0)}"
                }
            }
        }
    }

    private fun triggerAiScan() {
        lifecycleScope.launch {
            val btn = findViewById<Button>(R.id.btn_trigger_scan)
            btn?.isEnabled = false
            btn?.text = "MENGANALISIS..."

            try {
                val resStr = ApiClient.postData("/api/trigger-ai-scan", "{}")
                if (!resStr.isNullOrEmpty()) {
                    val scanRes = gson.fromJson(resStr, AiScanResponse::class.java)
                    findViewById<TextView>(R.id.tv_alpha_thesis)?.text = scanRes.decision?.alphaThesis ?: "-"
                    findViewById<TextView>(R.id.tv_risk_rebuttal)?.text = scanRes.decision?.riskRebuttal ?: "-"
                    Toast.makeText(this@MainActivity, "Keputusan AI: ${scanRes.decision?.action}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal audit AI", Toast.LENGTH_SHORT).show()
            } finally {
                btn?.isEnabled = true
                btn?.text = "⚡ PICU AUDIT AI LIVE"
            }
        }
    }
}
