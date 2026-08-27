package com.quant.terminal

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import com.google.gson.Gson
import com.quant.terminal.api.ApiClient
import com.quant.terminal.api.ChatHistoryItem
import com.quant.terminal.api.ChatRequest
import com.quant.terminal.api.ChatResponse
import kotlinx.coroutines.*
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBubble: View
    private lateinit var chatSheet: View
    private var isChatOpen = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val chatMemory = mutableListOf<ChatHistoryItem>()
    private val gson = Gson()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        val inflater = LayoutInflater.from(this)
        floatingBubble = inflater.inflate(R.layout.overlay_bubble, null)

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

                        if (abs(moveX) > 10 || abs(moveY) > 10) isClick = false

                        bubbleParams.x = initialX + moveX
                        bubbleParams.y = initialY + moveY
                        windowManager.updateViewLayout(floatingBubble, bubbleParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleChatSheet()
                        } else {
                            val screenWidth = resources.displayMetrics.widthPixels
                            bubbleParams.x = if (bubbleParams.x < screenWidth / 2) 0 else screenWidth
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
        val inflater = LayoutInflater.from(this)
        chatSheet = inflater.inflate(R.layout.overlay_bottomsheet, null)

        val btnSend = chatSheet.findViewById<Button>(R.id.btn_chat_send)
        val etInput = chatSheet.findViewById<EditText>(R.id.et_chat_input)
        val btnReset = chatSheet.findViewById<Button>(R.id.btn_reset_chat)
        val btnClose = chatSheet.findViewById<Button>(R.id.btn_close_sheet)

        val chipTrend = chatSheet.findViewById<Button>(R.id.chip_trend)
        val chipSnr = chatSheet.findViewById<Button>(R.id.chip_snr)
        val chipMacro = chatSheet.findViewById<Button>(R.id.chip_macro)

        btnSend.setOnClickListener {
            val msg = etInput.text.toString().trim()
            if (msg.isNotEmpty()) {
                etInput.setText("")
                sendUserMessage(msg)
            }
        }

        chipTrend.setOnClickListener { sendUserMessage("Bagaimana kesimpulan tren Gold & konfluensi saat ini?") }
        chipSnr.setOnClickListener { sendUserMessage("Apakah zona Demand/Supply terdekat aman untuk entry?") }
        chipMacro.setOnClickListener { sendUserMessage("Cek kondisi Order Flow CVD dan Indeks Tekanan Makro (MPI).") }

        btnReset.setOnClickListener {
            chatMemory.clear()
            chatSheet.findViewById<LinearLayout>(R.id.chat_message_container).removeAllViews()
            addAiBubble("Memori percakapan telah di-reset. Anda dapat memulai topik baru.")
        }

        btnClose.setOnClickListener {
            toggleChatSheet()
        }

        addAiBubble("Halo! Saya AI Trading Mentor dengan live data MT5. Tanyakan kondisi pasar terkini.")
    }

    private fun toggleChatSheet() {
        isChatOpen = !isChatOpen
        if (isChatOpen) {
            val chatParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.6).toInt(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                dimAmount = 0.4f
            }
            windowManager.addView(chatSheet, chatParams)
        } else {
            windowManager.removeView(chatSheet)
        }
    }

    private fun sendUserMessage(text: String) {
        addUserBubble(text)
        chatMemory.add(ChatHistoryItem(role = "user", text = text))

        serviceScope.launch {
            val loadingView = addLoadingBubble()
            val payload = gson.toJson(ChatRequest(message = text, history = chatMemory))

            try {
                val resStr = ApiClient.postData("/api/ai-mentor-chat", payload)
                removeBubble(loadingView)

                if (!resStr.isNullOrEmpty()) {
                    val res = gson.fromJson(resStr, ChatResponse::class.java)
                    val reply = res.reply ?: "Tidak ada respons dari AI."
                    chatMemory.add(ChatHistoryItem(role = "model", text = reply))
                    addAiBubbleTypewriter(reply)
                } else {
                    addAiBubble("Gagal menghubungi server AI Mentor.")
                }
            } catch (e: Exception) {
                removeBubble(loadingView)
                addAiBubble("Error: ${e.message}")
            }
        }
    }

    private fun addUserBubble(text: String) {
        val container = chatSheet.findViewById<LinearLayout>(R.id.chat_message_container)
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundResource(R.drawable.bg_chat_user)
            setPadding(20, 14, 20, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                topMargin = 10
                marginStart = 60
            }
            layoutParams = lp
        }
        container.addView(tv)
        scrollToBottom()
    }

    private fun addAiBubble(text: String) {
        val container = chatSheet.findViewById<LinearLayout>(R.id.chat_message_container)
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#e2e8f0"))
            textSize = 12f
            setBackgroundResource(R.drawable.bg_chat_ai)
            setPadding(20, 14, 20, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                topMargin = 10
                marginEnd = 60
            }
            layoutParams = lp
        }
        container.addView(tv)
        scrollToBottom()
    }

    private fun addAiBubbleTypewriter(fullText: String) {
        val container = chatSheet.findViewById<LinearLayout>(R.id.chat_message_container)
        val tv = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#e2e8f0"))
            textSize = 12f
            setBackgroundResource(R.drawable.bg_chat_ai)
            setPadding(20, 14, 20, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                topMargin = 10
                marginEnd = 60
            }
            layoutParams = lp
        }
        container.addView(tv)

        val words = fullText.split(" ")
        val handler = Handler(Looper.getMainLooper())
        var wordIndex = 0

        val runnable = object : Runnable {
            override fun run() {
                if (wordIndex < words.size) {
                    val currentStr = tv.text.toString()
                    tv.text = if (currentStr.isEmpty()) words[wordIndex] else "$currentStr ${words[wordIndex]}"
                    wordIndex++
                    scrollToBottom()
                    handler.postDelayed(this, 30)
                }
            }
        }
        handler.post(runnable)
    }

    private fun addLoadingBubble(): View {
        val container = chatSheet.findViewById<LinearLayout>(R.id.chat_message_container)
        val tv = TextView(this).apply {
            text = "Menganalisis data pasar..."
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 11f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                topMargin = 8
            }
            layoutParams = lp
        }
        container.addView(tv)
        scrollToBottom()
        return tv
    }

    private fun removeBubble(view: View) {
        val container = chatSheet.findViewById<LinearLayout>(R.id.chat_message_container)
        container.removeView(view)
    }

    private fun scrollToBottom() {
        val scroll = chatSheet.findViewById<ScrollView>(R.id.scroll_chat)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::floatingBubble.isInitialized) windowManager.removeView(floatingBubble)
        if (isChatOpen && ::chatSheet.isInitialized) windowManager.removeView(chatSheet)
    }
}
