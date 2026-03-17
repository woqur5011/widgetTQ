package com.fortq.wittq

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

data class YahooResponse(val chart: YahooChart)
data class YahooChart(val result: List<YahooResultData>?)
data class YahooResultData(
    val meta: YahooMeta,
    val indicators: YahooIndicators
)
data class YahooIndicators(val quote: List<YahooQuote>)
data class YahooQuote(val close: List<Double?>)

data class YahooMeta(
    val regularMarketPrice: Double, // 현재가
    val previousClose: Double
)

data class MarketData(
    val currentPrice: Double,
    val prevClose: Double,
    val history: List<Double>
)

interface YahooApiService {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getHistory(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "2y"
    ): YahooResponse
}

object StockApiEngine {
    private data class CacheEntry(val data: MarketData, val timestamp: Long)
    private val memCache = mutableMapOf<String, CacheEntry>()
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5분

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: YahooApiService = retrofit.create(YahooApiService::class.java)

    suspend fun fetchPrices(symbol: String): List<Double> {
        return fetchMarketData(symbol)?.history ?: emptyList()
    }

    suspend fun fetchMarketData(symbol: String): MarketData? {
        // 신선한 캐시가 있으면 즉시 반환 (rate-limit 방지)
        val cached = memCache[symbol]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.data
        }
        return try {
            val response = service.getHistory(symbol)
            val result = response.chart.result?.firstOrNull()
                ?: return memCache[symbol]?.data // 파싱 실패 시 stale 반환
            val data = MarketData(
                currentPrice = result.meta.regularMarketPrice,
                prevClose = result.meta.previousClose,
                history = result.indicators.quote.first().close.filterNotNull()
            )
            memCache[symbol] = CacheEntry(data, System.currentTimeMillis())
            data
        } catch (e: Exception) {
            Log.e("API_ERROR", "fetchMarketData($symbol) failed: ${e.message}")
            memCache[symbol]?.data // 네트워크 오류 시 stale 캐시 반환
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 위젯 차트 비트맵 파일 캐시 유틸
// ─────────────────────────────────────────────────────────────
object WidgetBitmapCache {
    fun save(context: Context, name: String, bitmap: Bitmap) {
        try {
            val dir = File(context.filesDir, "widget_cache")
            dir.mkdirs()
            FileOutputStream(File(dir, "$name.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, it)
            }
        } catch (e: Exception) { /* ignore */ }
    }

    fun load(context: Context, name: String): Bitmap? {
        return try {
            val file = File(context.filesDir, "widget_cache/$name.png")
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (e: Exception) { null }
    }
}