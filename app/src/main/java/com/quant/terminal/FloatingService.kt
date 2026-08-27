package com.quant.terminal

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Toast
import kotlinx.coroutines.*

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBubble: View
    private lateinit var chatSheet: View
    private var isChatOpen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 1. Setup Parameter UI Mengambang (Bubble)
        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.x = 0
        bubbleParams.y = 200

        // Inflate Layout XML (Kita akan buat layout XML nya di tahap 2)
        val inflater = LayoutInflater.from(this)
        floatingBubble = inflater.inflate(R.layout.overlay_bubble, null)
        
        // Logika Drag & Drop Bubble dengan auto-snap
        floatingBubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isClick = true

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveX = (event.rawX - initialTouchX).toInt()
                        val moveY = (event.rawY - initialTouchY).toInt()
                        
                        if (Math.abs(moveX) > 10 || Math.abs(moveY) > 10) isClick = false
                        
                        bubbleParams.x = initialX + moveX
                        bubbleParams.y = initialY + moveY
                        windowManager.updateViewLayout(floatingBubble, bubbleParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleChatSheet()
                        } else {
                            // Auto-snap ke pinggir layar
                            val screenWidth = resources.displayMetrics.widthPixels
                            if (bubbleParams.x < screenWidth / 2) bubbleParams.x = 0
                            else bubbleParams.x = screenWidth
                            windowManager.updateViewLayout(floatingBubble, bubbleParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingBubble, bubbleParams)
        setupChatSheet()
    }

    private fun setupChatSheet() {
        // Setup Jendela Chat (Awalnya tersembunyi/tidak dipasang)
        val inflater = LayoutInflater.from(this)
        chatSheet = inflater.inflate(R.layout.overlay_bottomsheet, null)
    }

    private fun toggleChatSheet() {
        isChatOpen = !isChatOpen
        if (isChatOpen) {
            val chatParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.6).toInt(), // 60% layar HP
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_DIM_BEHIND, // Biarkan background sedikit gelap tapi MT5 tetap kelihatan
                PixelFormat.TRANSLUCENT
            )
            chatParams.gravity = Gravity.BOTTOM
            chatParams.dimAmount = 0.4f // Transparansi dark mode
            windowManager.addView(chatSheet, chatParams)
        } else {
            windowManager.removeView(chatSheet)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingBubble.isInitialized) windowManager.removeView(floatingBubble)
        if (isChatOpen && ::chatSheet.isInitialized) windowManager.removeView(chatSheet)
    }
}
