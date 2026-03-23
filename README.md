# widgetTQ

TQQQ 투자 전략 기반 안드로이드 홈 화면 위젯 모음입니다.  
Yahoo Finance API에서 실시간 시세를 가져와 각 전략의 매수·보유·청산 신호를 홈 화면에 표시합니다.

---

## 위젯 전략 목록

| 위젯 이름 | 핵심 지표 | 신호 종류 |
|----------|---------|---------|
| **200MA TQ** | TQQQ 200일 이동평균 | TQ입학 / 중도입학 / 졸업 |
| **5/220 TQ** | TQQQ 5MA·220MA + QQQ 52주 고점 이격 + RSI | 매수 / 분할 / 눈덩이(DIP) / 전량매도 |
| **3/161 TQ** | QQQ 3MA·161MA + TQQQ 이격도 + SPY + 변동성 | HOLD / ESCAPE / STOP / Cooling |
| **3/161/185 TQ** | QQQ 3MA·161MA·185MA 배열 | BUY / HOLD / SELL |
| **FGI** | CNN Fear & Greed Index | 공포/탐욕 지수 표시 |

---

## 기술 스택

- **언어**: Kotlin
- **위젯 UI**: Jetpack Glance (Compose 스타일)
- **백그라운드 갱신**: WorkManager (PeriodicWork, 60분 주기)
- **시세 API**: Yahoo Finance v8 (Retrofit2 + Gson)
- **데이터 캐시**: 인메모리 5분 캐시 + SharedPreferences + 비트맵 파일 캐시
- **최소 SDK**: API 26 (Android 8.0)

---

## 프로젝트 구조

```
app/src/main/java/com/fortq/wittq/
│
├── MarketDataEngine.kt          # Yahoo Finance API 클라이언트 + 5분 인메모리 캐시
│
├── [200MA TQ]
│   ├── TqAlgorithms.kt          # Ma200Strategy, Tq3161Algorithm 계산 로직
│   ├── Ma200Widget.kt           # GlanceAppWidget UI
│   ├── Ma200WidgetReceiver.kt   # GlanceAppWidgetReceiver
│   └── Ma200UpdateWorker.kt     # PeriodicWorkRequest
│
├── [5/220 TQ]
│   ├── Tq5220Algorithm.kt       # Tq5220Strategy 계산 로직 (DIP1/DIP2/졸업 포함)
│   ├── Tq5220Widget.kt
│   ├── Tq5220WidgetReceiver.kt
│   └── Tq5220UpdateWorker.kt
│
├── [3/161 TQ]
│   ├── TqAlgorithms.kt          # Tq3161Algorithm (공유 파일)
│   ├── Tq3161Widget.kt
│   ├── Tq3161WidgetReceiver.kt
│   ├── Tq3161UpdateWorker.kt
│   └── Tq3161PrefManager.kt
│
├── [3/161/185 TQ]
│   ├── Tq3161185Algorithm.kt
│   ├── Tq3161185SignalWidget.kt
│   ├── Tq3161185SignalWidgetReceiver.kt
│   └── Tq3161185SignalWorker.kt
│
└── [FGI]
    ├── FGIApiEngine.kt
    ├── FGIWidget.kt
    ├── FGIWidgetReceiver.kt
    └── FGIUpdateWorker.kt
```

---

## 5/220 TQ 전략 상세 (Snowball)

TQQQ 5일 이동평균과 220일 이동평균, QQQ 52주 고점 이격도를 조합합니다.

### 매수 비중 결정 (`buyRatio`)

| 조건 | 비중 |
|------|------|
| 쿨다운 중 | 0% |
| DIP 평단 대비 +350% 이상 | 0% (청산) |
| DIP 평단 대비 +68% 이상 | 35% |
| DIP 평단 대비 +15% 이상 | 50% |
| QQQ 52주 고점 대비 -40% 이하 | 0% (매수 중단) |
| QQQ 52주 고점 대비 -22% 이하 (DIP2) | 70% |
| QQQ 52주 고점 대비 -10% 이하 (DIP1) + RSI ≤ 25 | 50% |
| QQQ 52주 고점 대비 -10% 이하 (DIP1) + RSI ≤ 35 | 40% |
| QQQ 52주 고점 대비 -10% 이하 (DIP1) | 30% |
| 골든크로스 (5MA > 220MA) | 100% |

### 보유 비중 결정 (`tqRatio`)

| 조건 | 비중 |
|------|------|
| 220MA 이탈 (약세 전환) | 0% (전량청산) |
| 진입가 대비 +350% 이상 | 0% (졸업 청산) |
| 진입가 대비 +68% 이상 | 35% |
| 진입가 대비 +15% 이상 | 50% |
| 그 외 | 100% |

---

## 설정 방법

1. Android Studio에서 프로젝트 열기
2. 앱 실행 후 평단가·포지션 입력
3. 홈 화면 위젯 목록에서 원하는 전략 위젯 추가

> 설정값(`user_avg_price`, `user_position`)은 `AppPrefs` SharedPreferences에 저장되며 모든 위젯이 공유합니다.

---

## 데이터 갱신 구조

```
홈 화면 위젯 표시
    └── provideGlance() 호출
          ├── WorkManager PeriodicWork 등록 (60분, KEEP)
          ├── MarketDataEngine.fetchMarketData() (5분 인메모리 캐시)
          │     └── Yahoo Finance API
          ├── 성공 → SharedPrefs + 파일 비트맵 캐시 저장
          └── 실패 → 캐시 복원 (위젯 공백 방지)
```

---

## 전략 출처

아래 전략들을 코드로 구현한 개인 프로젝트입니다.

- TQQQ 200MA 전략 — 아기티큐
- TQQQ 5/220MA + QQQ 전략 — 호리오리 (Snowball)
- QQQ 3/161 복합 전략 — 김쨰아빠
- QQQ 3/161/185 전략 — QQQ 3_161_185.js 레퍼런스

---

## 주의사항

- Yahoo Finance 비공식 API를 사용합니다. 서비스 정책 변경 시 동작이 중단될 수 있습니다.
- 이 앱은 **투자 참고용**이며, 실제 투자 결정에 대한 책임은 사용자 본인에게 있습니다.


> 이 앱은 투자 조언이 아닙니다. 모든 투자 결정은 본인 책임입니다.
