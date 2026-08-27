package com.quant.terminal.api

import com.google.gson.annotations.SerializedName

// 1. Response Market Pulse (/api/market-pulse)
data class MarketPulseResponse(
    val status: String,
    val symbol: String?,
    val candles: List<CandleItem>?,
    @SerializedName("sma100_value") val sma100Value: Double?,
    @SerializedName("live_tick") val liveTick: LiveTick?,
    val analysis: AnalysisData?
)

data class CandleItem(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class LiveTick(
    val bid: Double,
    val ask: Double,
    @SerializedName("spread_pts") val spreadPts: Int,
    @SerializedName("spread_price") val spreadPrice: Double
)

data class AnalysisData(
    @SerializedName("h1_snr") val h1Snr: H1Snr?,
    @SerializedName("m5_indicators") val m5Indicators: M5Indicators?,
    @SerializedName("cvd_order_flow") val cvdOrderFlow: CvdOrderFlow?,
    @SerializedName("market_regime") val marketRegime: MarketRegime?,
    @SerializedName("global_macro_intelligence") val macroIntel: MacroIntel?
)

data class H1Snr(
    val support: Double?,
    val resistance: Double?,
    val pivot: Double?
)

data class M5Indicators(
    @SerializedName("current_price") val currentPrice: Double?,
    val atr: Double?,
    val rsi: Double?,
    val macd: Double?
)

data class CvdOrderFlow(
    @SerializedName("cvd_bias") val cvdBias: String?,
    @SerializedName("absorption_status") val absorptionStatus: String?,
    @SerializedName("delta_flow") val deltaFlow: Double?
)

data class MarketRegime(
    @SerializedName("choppiness_index") val choppinessIndex: Double?,
    @SerializedName("efficiency_ratio_ker") val ker: Double?,
    @SerializedName("regime_status") val regimeStatus: String?
)

data class MacroIntel(
    @SerializedName("macro_pressure_index") val mpi: Double?,
    @SerializedName("macro_regime_bias") val macroBias: String?,
    @SerializedName("us_dollar_dxy_change_pct") val dxyChange: Double?,
    @SerializedName("us10y_yield_change_pct") val us10yChange: Double?,
    @SerializedName("vix_volatility_index") val vix: Double?
)

// 2. Response AI Scan (/api/trigger-ai-scan)
data class AiScanResponse(
    val status: String,
    val decision: AiDecision?,
    @SerializedName("current_price") val currentPrice: Double?,
    @SerializedName("macro_mpi") val macroMpi: Double?,
    @SerializedName("macro_bias") val macroBias: String?
)

data class AiDecision(
    val action: String?,
    val confidence: Double?,
    @SerializedName("target_sl") val targetSl: Double?,
    @SerializedName("target_tp") val targetTp: Double?,
    @SerializedName("limit_price") val limitPrice: Double?,
    @SerializedName("alpha_thesis") val alphaThesis: String?,
    @SerializedName("risk_rebuttal") val riskRebuttal: String?,
    val reason: String?
)

// 3. Payload AI Mentor Chat (/api/ai-mentor-chat)
data class ChatHistoryItem(
    val role: String,
    val text: String
)

data class ChatRequest(
    val message: String,
    val history: List<ChatHistoryItem> = emptyList()
)

data class ChatResponse(
    val status: String,
    val reply: String?
)
