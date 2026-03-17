package com.fortq.wittq

// ============================================================
// QQQ 3/161/185 전략 알고리즘 (Kotlin)
//
// JS 레퍼런스 핵심 로직:
//   1) 3일선이 161일선을 하향돌파한 당일 → 무조건 매도 (예외매수 금지)
//   2) normalState (3 > 161)          → 보유(기본),  매도선 161일선
//   3) exceptionState (env > 161 > 3 > 185) → 보유(예외), 매도선 185일선
//   4) below185State (3 < 185)        → 전량 매도 / 미보유
//   5) 미보유 진입:
//      기본  : 3일선이 185일선 상향돌파 or 밴드 진입 + 3 > 161
//      예외  : exceptionState (하향돌파 당일 제외)
// ============================================================

data class Tq3161185ChartBar(
    val close: Double,
    val ma3: Double?,
    val ma161: Double?,
    val ma185: Double?,
    val envUpper: Double?
)

data class Tq3161185Result(
    val state: String,           // 미보유 / 보유(기본) / 보유(예외)
    val activeSellLine: String,  // - / 161일선 / 185일선
    val entryPrice: Double?,     // 전략 진입 시 TQQQ 가격
    val entryReason: String,     // 진입 사유
    val daysInPosition: Int,     // 진입 후 경과 거래일
    val todaySignal: String,     // 매수 / 매도 / 보유 / 관망
    val todayReason: String,     // 신호 사유
    val strategyReturnPct: Double?,
    val qqqCurrentPrice: Double,
    val tqqqCurrentPrice: Double,
    val tqqqChangePct: Double?,
    val ma3: Double?,
    val ma161: Double?,
    val ma185: Double?,
    val envUpper: Double?,
    val signalColor: Long,
    val chartBars: List<Tq3161185ChartBar>
) {
    val isHolding: Boolean get() = state.startsWith("보유")
}

object Tq3161185Algorithm {

    private const val SHORT_MA     = 3
    private const val SELL_MA      = 161
    private const val BASE_MA      = 185
    private const val ENVELOPE_PCT = 0.05
    private const val CHART_DAYS   = 90

    // 내부 bar 데이터 (시뮬레이션 전용)
    private data class Bar(
        val close: Double,
        val tClose: Double,
        val ma3: Double?,
        val ma161: Double?,
        val ma185: Double?,
        val envUpper: Double?
    )

    private fun sma(closes: List<Double>, endIdx: Int, period: Int): Double? {
        if (endIdx - period + 1 < 0) return null
        return closes.subList(endIdx - period + 1, endIdx + 1).average()
    }

    fun calculate(
        qPrices: List<Double>,          // QQQ 일별 종가
        tPrices: List<Double>,          // TQQQ 일별 종가
        tqqqCurrentPrice: Double,       // TQQQ 실시간 현재가
        tqqqPrevClose: Double?          // TQQQ 전일 종가 (등락률 계산용)
    ): Tq3161185Result {

        // 양쪽 길이 맞추기 (Yahoo API 응답 편차 대응)
        val size = minOf(qPrices.size, tPrices.size)
        if (size < BASE_MA + 2) {
            return insufficientDataResult(tqqqCurrentPrice, tqqqPrevClose)
        }

        val qCloses = qPrices.takeLast(size)
        val tCloses = tPrices.takeLast(size)

        // 전체 bar 생성
        val bars = (0 until size).map { i ->
            val ma185 = sma(qCloses, i, BASE_MA)
            Bar(
                close    = qCloses[i],
                tClose   = tCloses[i],
                ma3      = sma(qCloses, i, SHORT_MA),
                ma161    = sma(qCloses, i, SELL_MA),
                ma185    = ma185,
                envUpper = ma185?.times(1.0 + ENVELOPE_PCT)
            )
        }

        // ────────────────────────────────────────
        // 상태 시뮬레이션
        // ────────────────────────────────────────
        var state          = "미보유"
        var activeSellLine = "-"
        var entryPrice: Double? = null
        var entryReason    = "-"
        var daysInPosition = 0
        var todaySignal    = "관망"
        var todayReason    = "-"

        for (i in 1 until bars.size) {
            val prev = bars[i - 1]
            val curr = bars[i]

            // MA 계산이 아직 준비되지 않은 초반 bar 건너뜀
            if (prev.ma3 == null || prev.ma161 == null || prev.ma185 == null || prev.envUpper == null ||
                curr.ma3 == null || curr.ma161 == null || curr.ma185 == null || curr.envUpper == null
            ) continue

            val hadPosition = state.startsWith("보유")
            if (hadPosition) daysInPosition++

            val crossDown161 = prev.ma3 >= prev.ma161 && curr.ma3 < curr.ma161
            val crossUp185   = prev.ma3 < prev.ma185  && curr.ma3 >= curr.ma185

            val normalState    = curr.ma3 > curr.ma161
            val exceptionState = curr.envUpper > curr.ma161 &&
                                 curr.ma161   > curr.ma3    &&
                                 curr.ma3     > curr.ma185
            val below185State  = curr.ma3 < curr.ma185
            val bandEntry      = curr.ma3 >= curr.ma185 && curr.ma3 <= curr.envUpper

            var traded = false

            // ── 규칙 1: 3일선이 161일선을 하향돌파한 당일 ──────────────────
            if (crossDown161) {
                if (hadPosition) {
                    state = "미보유"; activeSellLine = "-"
                    daysInPosition = 0; entryPrice = null; entryReason = "-"
                    todaySignal = "매도"; todayReason = "3일선 161일선 하향돌파"
                } else {
                    todaySignal = "관망"; todayReason = "3일선 161일선 하향돌파(진입 금지)"
                }
                traded = true
            }

            // ── 규칙 2: 기존 보유 포지션 관리 ──────────────────────────────
            if (!traded && hadPosition) {
                when {
                    below185State -> {
                        state = "미보유"; activeSellLine = "-"
                        daysInPosition = 0; entryPrice = null; entryReason = "-"
                        todaySignal = "매도"; todayReason = "3일선 185일선 하향이탈"
                        traded = true
                    }
                    exceptionState -> {
                        val wasException = state == "보유(예외)"
                        state = "보유(예외)"; activeSellLine = "185일선"
                        todaySignal = "보유"
                        todayReason = if (wasException) "예외 상태 유지" else "예외 상태 전환"
                    }
                    normalState -> {
                        state = "보유(기본)"; activeSellLine = "161일선"
                        todaySignal = "보유"; todayReason = "기본 상태 유지"
                    }
                    else -> {
                        state = "미보유"; activeSellLine = "-"
                        daysInPosition = 0; entryPrice = null; entryReason = "-"
                        todaySignal = "관망"; todayReason = "재진입 조건 미충족"
                    }
                }
            }

            // ── 규칙 3: 미보유 → 신규 진입 판단 ────────────────────────────
            if (!traded && state == "미보유") {
                when {
                    exceptionState -> {
                        state = "보유(예외)"; activeSellLine = "185일선"
                        entryPrice = curr.tClose; entryReason = "예외 배열 진입"
                        daysInPosition = 1
                        todaySignal = "매수"; todayReason = "예외 배열 진입"
                    }
                    (crossUp185 || bandEntry) && normalState -> {
                        state = "보유(기본)"; activeSellLine = "161일선"
                        entryReason = if (crossUp185) "185일선 상향돌파" else "185일선 +5% 밴드 진입"
                        entryPrice = curr.tClose; daysInPosition = 1
                        todaySignal = "매수"; todayReason = entryReason
                    }
                    else -> {
                        todaySignal = "관망"
                        todayReason = if (below185State) "3일선 185일선 아래" else "진입 조건 미충족"
                    }
                }
            }
        }

        // ── 최신 MA 값 (표시용) ─────────────────────────────────────────────
        val last         = bars.last()
        val latestMa3    = last.ma3
        val latestMa161  = last.ma161
        val latestMa185  = last.ma185
        val latestEnvUpper = last.envUpper

        // ── 전략 수익률 계산 ────────────────────────────────────────────────
        val strategyReturnPct = if (state.startsWith("보유") && entryPrice != null && entryPrice!! > 0) {
            ((tqqqCurrentPrice / entryPrice!!) - 1) * 100
        } else null

        // ── TQQQ 등락률 ─────────────────────────────────────────────────────
        val tqqqChangePct = if (tqqqPrevClose != null && tqqqPrevClose > 0) {
            ((tqqqCurrentPrice / tqqqPrevClose) - 1) * 100
        } else null

        val signalColor: Long = when (todaySignal) {
            "매수" -> 0xFF30D158
            "매도" -> 0xFFFF453A
            "보유" -> 0xFF0A84FF
            else   -> 0xFF8E8E93
        }

        // ── 차트용 데이터 (최근 90 bar) ─────────────────────────────────────
        val chartBars = bars.takeLast(CHART_DAYS).map { bar ->
            Tq3161185ChartBar(bar.close, bar.ma3, bar.ma161, bar.ma185, bar.envUpper)
        }

        return Tq3161185Result(
            state              = state,
            activeSellLine     = activeSellLine,
            entryPrice         = entryPrice,
            entryReason        = entryReason,
            daysInPosition     = daysInPosition,
            todaySignal        = todaySignal,
            todayReason        = todayReason,
            strategyReturnPct  = strategyReturnPct,
            qqqCurrentPrice    = qCloses.last(),
            tqqqCurrentPrice   = tqqqCurrentPrice,
            tqqqChangePct      = tqqqChangePct,
            ma3                = latestMa3,
            ma161              = latestMa161,
            ma185              = latestMa185,
            envUpper           = latestEnvUpper,
            signalColor        = signalColor,
            chartBars          = chartBars
        )
    }

    private fun insufficientDataResult(
        tqqqCurrentPrice: Double,
        tqqqPrevClose: Double?
    ) = Tq3161185Result(
        state              = "데이터 부족",
        activeSellLine     = "-",
        entryPrice         = null,
        entryReason        = "-",
        daysInPosition     = 0,
        todaySignal        = "관망",
        todayReason        = "히스토리 부족 (최소 ${BASE_MA + 2}일 필요)",
        strategyReturnPct  = null,
        qqqCurrentPrice    = 0.0,
        tqqqCurrentPrice   = tqqqCurrentPrice,
        tqqqChangePct      = if (tqqqPrevClose != null && tqqqPrevClose > 0)
                                ((tqqqCurrentPrice / tqqqPrevClose) - 1) * 100 else null,
        ma3                = null,
        ma161              = null,
        ma185              = null,
        envUpper           = null,
        signalColor        = 0xFF8E8E93,
        chartBars          = emptyList()
    )
}
