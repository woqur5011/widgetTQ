package com.fortq.wittq

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

// ─────────────────────────────────────────────────────────────
// 새로고침 버튼 콜백
// ─────────────────────────────────────────────────────────────
class Tq3161185RefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            Log.d("WITTQ_DEBUG", "QqqqStrategy refresh clicked")
            Tq3161185SignalWidget().updateAll(context)
        } catch (e: Exception) {
            Log.e("WITTQ_DEBUG", "QqqqStrategy refresh failed: ${e.message}", e)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// QQQ 3/161/185 전략 위젯
// ─────────────────────────────────────────────────────────────
class Tq3161185SignalWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(300.dp, 100.dp), DpSize(412.dp, 150.dp))
    )

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try { Tq3161185SignalWorker.enqueue(context) } catch (e: Exception) {
            Log.e("WITTQ_DEBUG", "Worker enqueue failed: ${e.message}", e)
        }

        val lastUpdate = SimpleDateFormat(
            "HH:mm:ss", java.util.Locale.getDefault()
        ).format(Date())

        val resultPair = withContext(Dispatchers.IO) {
            try {
                val qData = StockApiEngine.fetchMarketData("QQQ")
                    ?: return@withContext null
                val tData = StockApiEngine.fetchMarketData("TQQQ")
                    ?: return@withContext null

                val strategy = Tq3161185Algorithm.calculate(
                    qPrices          = qData.history,
                    tPrices          = tData.history,
                    tqqqCurrentPrice = tData.currentPrice,
                    tqqqPrevClose    = tData.prevClose
                )

                val chart = drawStrategyChart(strategy.chartBars, 400, 400)
                Pair(strategy, chart)
            } catch (e: Exception) {
                Log.e("WITTQ_DEBUG", "QqqqStrategy data failed: ${e.message}", e)
                null
            }
        }

        provideContent {
            val size = LocalSize.current
            if (resultPair != null) {
                val (strategy, chart) = resultPair
                WidgetContent(strategy, chart, lastUpdate, size)
            } else {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFF1C1C1E))
                        .cornerRadius(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "QQQ 3/161/185",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF8E8E93)),
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            "업데이트 중...",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 14.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            lastUpdate,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF8E8E93)),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 위젯 UI
    // ─────────────────────────────────────────────────────────
    @SuppressLint("DefaultLocale", "RestrictedApi")
    @Composable
    private fun WidgetContent(
        res: Tq3161185Result,
        chart: Bitmap?,
        updateTime: String,
        size: DpSize
    ) {
        val factor    = (size.width.value / 410f).coerceIn(0.6f, 1.0f)
        val hpadding  = (16 * factor).dp
        val vpadding  = (12 * factor).dp

        // 색상 팔레트
        val signalColor = Color(res.signalColor)
        val upColor     = Color(0xFF30D158)
        val downColor   = Color(0xFFFF453A)
        val gray        = Color(0xFF8E8E93)
        val white       = Color.White
        val amber       = Color(0xFFF59E0B) // MA3
        val blue        = Color(0xFF3B82F6) // MA161
        val green       = Color(0xFF10B981) // MA185

        // 폰트 크기
        val tinySize  = (9 * factor).sp
        val subSize   = (10 * factor).sp
        val mainSize  = (12 * factor).sp
        val priceSize = (17 * factor).sp

        val changePct  = res.tqqqChangePct
        val changeColor = when {
            changePct == null   -> gray
            changePct >= 0      -> upColor
            else                -> downColor
        }
        val changeStr  = changePct?.let {
            "${if (it >= 0) "+" else ""}${"%.2f".format(it)}%"
        } ?: "-"

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
                .cornerRadius(42.dp)
                .padding(horizontal = hpadding, vertical = vpadding)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // ── 좌측: 차트 (최대 확장) ──────────────────────────────
                    Column(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 차트 이미지 (가용 공간 최대 확장)
                        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                            chart?.let {
                                Image(
                                    provider = ImageProvider(it),
                                    contentDescription = null,
                                    modifier = GlanceModifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.height((3 * factor).dp))

                        // MA 값 표시 (차트 하단에만)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "3: ${res.ma3?.let { "%.0f".format(it) } ?: "-"}",
                                style = TextStyle(
                                    color = ColorProvider(amber),
                                    fontSize = tinySize
                                )
                            )
                            Spacer(modifier = GlanceModifier.width(5.dp))
                            Text(
                                "161: ${res.ma161?.let { "%.0f".format(it) } ?: "-"}",
                                style = TextStyle(
                                    color = ColorProvider(blue),
                                    fontSize = tinySize
                                )
                            )
                            Spacer(modifier = GlanceModifier.width(5.dp))
                            Text(
                                "185: ${res.ma185?.let { "%.0f".format(it) } ?: "-"}",
                                style = TextStyle(
                                    color = ColorProvider(green),
                                    fontSize = tinySize
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.width((15 * factor).dp))

                    // ── 우측: 신호 / 상태 정보 ─────────────────────────────
                    Column(modifier = GlanceModifier.width((130 * factor).dp).fillMaxHeight()) {
                        Text(
                            "QQQ 3/161/185",
                            style = TextStyle(
                                fontSize = (13 * factor).sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color.LightGray)
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // SIGNAL
                        Text(
                            "SIGNAL",
                            style = TextStyle(color = ColorProvider(gray), fontSize = mainSize)
                        )
                        Text(
                            res.todaySignal,
                            style = TextStyle(
                                color = ColorProvider(signalColor),
                                fontSize = priceSize,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            res.todayReason,
                            style = TextStyle(
                                color = ColorProvider(gray),
                                fontSize = (9 * factor).sp
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // STATUS + 매도선
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(
                                    "STATUS",
                                    style = TextStyle(color = ColorProvider(gray), fontSize = tinySize)
                                )
                                Text(
                                    res.state,
                                    style = TextStyle(
                                        color = ColorProvider(white),
                                        fontSize = subSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = GlanceModifier.width((10 * factor).dp))
                            Column {
                                Text(
                                    "매도선",
                                    style = TextStyle(color = ColorProvider(gray), fontSize = tinySize)
                                )
                                Text(
                                    res.activeSellLine,
                                    style = TextStyle(
                                        color = ColorProvider(white),
                                        fontSize = subSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // TQQQ 현재가 + 등락률
                        Text(
                            "TQQQ $${"%.2f".format(res.tqqqCurrentPrice)}",
                            style = TextStyle(
                                color = ColorProvider(white),
                                fontSize = mainSize,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            changeStr,
                            style = TextStyle(color = ColorProvider(changeColor), fontSize = subSize)
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // 전략 수익률 + 새로고침
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                if (res.isHolding && res.strategyReturnPct != null) {
                                    Text(
                                        "전략수익",
                                        style = TextStyle(
                                            color = ColorProvider(gray),
                                            fontSize = tinySize
                                        )
                                    )
                                    val retStr = "${if (res.strategyReturnPct >= 0) "+" else ""}${"%.2f".format(res.strategyReturnPct)}%"
                                    Text(
                                        retStr,
                                        style = TextStyle(
                                            color = ColorProvider(
                                                if (res.strategyReturnPct >= 0) upColor else downColor
                                            ),
                                            fontSize = mainSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                } else {
                                    // 진입가 표시 (보유 중이지만 수익률 없는 경우 or 미보유)
                                    val entryStr = res.entryPrice
                                        ?.let { "진입 $${"%.2f".format(it)}" }
                                        ?: "미보유"
                                    Text(
                                        entryStr,
                                        style = TextStyle(color = ColorProvider(gray), fontSize = tinySize)
                                    )
                                    if (res.daysInPosition > 0) {
                                        Text(
                                            "${res.daysInPosition}일째",
                                            style = TextStyle(color = ColorProvider(gray), fontSize = tinySize)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = GlanceModifier.defaultWeight())

                            // 새로고침 버튼
                            Box(
                                modifier = GlanceModifier
                                    .size((30 * factor).dp)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .cornerRadius((15 * factor).dp)
                                    .clickable(actionRunCallback<Tq3161185RefreshCallback>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_refresh),
                                    contentDescription = "Refresh",
                                    modifier = GlanceModifier.size((16 * factor).dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 차트 그리기
    // 라인: QQQ 종가(흰색), MA3(황색), MA161(청색), MA185(녹색), Env(적색)
    // JS settings.colors 색상 그대로 유지
    // ─────────────────────────────────────────────────────────
    private fun drawStrategyChart(bars: List<Tq3161185ChartBar>, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경 (JS SETTINGS.colors.panel)
        canvas.drawColor(android.graphics.Color.parseColor("#0b1220"))

        val pad    = 6f
        val chartW = width  - pad * 2
        val chartH = height - pad * 2

        // 값 범위 계산
        val allValues = bars.flatMap { bar ->
            listOfNotNull(bar.close, bar.ma3, bar.ma161, bar.ma185, bar.envUpper)
        }
        if (allValues.isEmpty()) return bitmap

        var minV = allValues.min()
        var maxV = allValues.max()
        if (maxV == minV) { maxV += 1.0; minV -= 1.0 }
        val range = maxV - minV

        fun getY(v: Double): Float = height - pad - ((v - minV) / range * chartH).toFloat()
        fun getX(i: Int):  Float  = pad + (i.toFloat() / (bars.size - 1).coerceAtLeast(1)) * chartW

        // 격자선 (3줄)
        val gridPaint = Paint().apply {
            color       = android.graphics.Color.parseColor("#374151")
            alpha       = 115
            style       = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        for (k in 0..2) {
            val y = pad + (chartH / 2) * k
            canvas.drawLine(pad, y, pad + chartW, y, gridPaint)
        }

        // 시리즈 정의 (JS settings.colors 동일)
        data class Series(
            val hexColor: String,
            val strokeWidth: Float,
            val getValue: (Tq3161185ChartBar) -> Double?
        )

        val seriesList = listOf(
            Series("#e5e7eb", 3.5f) { it.close    },  // QQQ 종가 (흰회색)
            Series("#f59e0b", 2.5f) { it.ma3      },  // MA3  (황색)
            Series("#3b82f6", 2.0f) { it.ma161    },  // MA161 (청색)
            Series("#10b981", 2.0f) { it.ma185    },  // MA185 (녹색)
            Series("#ef4444", 2.0f) { it.envUpper }   // Env+5% (적색)
        )

        for (s in seriesList) {
            val points = bars.mapIndexedNotNull { i, bar ->
                val v = s.getValue(bar) ?: return@mapIndexedNotNull null
                PointF(getX(i), getY(v))
            }
            if (points.size < 2) continue

            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (p in points.drop(1)) lineTo(p.x, p.y)
            }
            val paint = Paint().apply {
                color       = android.graphics.Color.parseColor(s.hexColor)
                style       = Paint.Style.STROKE
                strokeWidth = s.strokeWidth
                isAntiAlias = true
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }
}
