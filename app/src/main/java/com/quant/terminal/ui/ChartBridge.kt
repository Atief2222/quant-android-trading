package com.quant.terminal.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.quant.terminal.api.CandleItem

object ChartBridge {

    fun initChart(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="https://unpkg.com/lightweight-charts@4.1.1/dist/lightweight-charts.standalone.production.js"></script>
                <style>body { margin: 0; background: #0f172a; overflow: hidden; }</style>
            </head>
            <body>
                <div id="chart" style="width: 100vw; height: 100vh;"></div>
                <script>
                    let chart = LightweightCharts.createChart(document.getElementById('chart'), {
                        layout: { background: { color: '#0f172a' }, textColor: '#94a3b8' },
                        grid: { vertLines: { color: '#1e293b' }, horzLines: { color: '#1e293b' } },
                        timeScale: { timeVisible: true }
                    });
                    let candleSeries = chart.addCandlestickSeries({
                        upColor: '#10b981', downColor: '#ef4444',
                        borderUpColor: '#10b981', borderDownColor: '#ef4444',
                        wickUpColor: '#10b981', wickDownColor: '#ef4444'
                    });

                    function updateCandles(jsonStr) {
                        try {
                            let data = JSON.parse(jsonStr);
                            candleSeries.setData(data);
                        } catch(e) {}
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    fun renderCandles(webView: WebView, candles: List<CandleItem>) {
        val json = Gson().toJson(candles)
        webView.post {
            webView.evaluateJavascript("javascript:updateCandles('$json')", null)
        }
    }
}
