# Trade System Architecture & Package Structure (Java 중심 + Python 보조)

> 목표: **Java(Spring Boot) 메인 트레이딩/백테스트 엔진**을 만들고,  
> Python은 **크롤링/커뮤니티 지수 산출 + 간단 대시보드(Streamlit/Dash)** 역할만 담당한다.

---

## 1. 전체 아키텍처 개요

### 1.1 구성요소(논리)

- **Java (Core)**
  - Strategy Engine (시그널 생성)
  - Backtest Engine (시뮬/리포트)
  - Execution Engine (실시간 주문/체결 처리)
  - Risk Engine (손실 제한/포지션 사이징/중복주문 방지)
  - KIS Adapter (REST/WS, 인증/레이트리밋/재시도)
  - Scheduler/Batch (장 시작/마감, 데이터 동기화)
  - REST API (운영/백테스트 실행/상태 조회)
  - MySQL 저장소

- **Python (Support)**
  - 커뮤니티/뉴스 등 **허용된 소스** 수집(크롤링)
  - 찬티/안티/버즈 지수 산출(가벼운 룰/통계 기반)
  - Streamlit/Dash 대시보드(Java API 또는 MySQL 조회)

### 1.2 데이터 흐름(권장)

1) 시세/체결/잔고 → **Java**로 수집/저장  
2) 커뮤니티 지수 → **Python**이 산출 후 **MySQL 적재**(또는 Java API 호출)  
3) 전략 엔진이 가격/거래대금/커뮤니티 지수 등을 조회해 **신호 생성**  
4) 실행 엔진이 리스크 엔진을 통과한 주문만 KIS로 송신  
5) 체결/잔고 이벤트를 받아 포트폴리오 상태 업데이트  
6) 대시보드는 Java API로 현황/리포트 시각화

---

## 2. 레포 구조(Gradle 멀티모듈 권장)

```text
trade-system/
├─ settings.gradle
├─ build.gradle
├─ docker/
│  └─ mysql/docker-compose.yml
├─ apps/
│  └─ api-server/                 # Spring Boot 실행 모듈(조립/런타임)
│     ├─ build.gradle
│     └─ src/main/java/...
├─ modules/
│  ├─ domain/                     # 순수 도메인(외부 의존 X)
│  ├─ usecase/                    # 애플리케이션 유즈케이스(포트/트랜잭션)
│  ├─ web/                        # REST 컨트롤러/DTO/Validation/OpenAPI
│  ├─ backtest/                   # 백테스트 엔진/리포트
│  └─ infra/
│     ├─ persistence-mysql/       # JPA/QueryDSL, Port Adapter 구현
│     ├─ market-kis/              # KIS REST/WS, BrokerPort 구현
│     └─ messaging/               # (선택) 알림/이벤트
├─ python/
│  ├─ crawler/                    # 커뮤니티/뉴스 수집(허용 소스)
│  ├─ sentiment/                  # 지수 산출
│  └─ dashboard/                  # Streamlit/Dash
└─ docs/
   ├─ architecture.md
   └─ strategy-spec.md
```

---

## 3. Java 패키지 구조(모듈별)

### 3.1 modules/domain (순수 코어)

```text
com.yourorg.trade.domain
├─ common/            # Money/Price/Qty/Percent, 공통 유틸(순수)
├─ instrument/        # Ticker, Exchange, Market
├─ marketdata/        # Candle, Tick(선택), Indicators(SMA/EMA/VWAP/ATR/RSI)
├─ strategy/          # Strategy, Signal, 전략 구현(orb/vwap/gap/atr/mr)
├─ risk/              # RiskPolicy, PositionSizing, DailyLossLimit 등
├─ order/             # Order, Side, OrderType, TimeInForce, Fill
├─ portfolio/         # Position, Portfolio, PnL
└─ clock/             # TradingClock(실시간/백테스트 공용)
```

원칙:
- domain은 **Spring/JPA/WebClient/Jackson** 등 외부 프레임워크 의존을 금지한다.
- 전략/리스크 로직은 domain에 두어 **백테스트와 실거래가 공유**한다.

### 3.2 modules/usecase (애플리케이션 계층)

```text
com.yourorg.trade.usecase
├─ trading/
│  ├─ RunStrategyUseCase
│  ├─ PlaceOrderUseCase
│  ├─ CancelOrderUseCase
│  ├─ SyncAccountStateUseCase
│  └─ DailyResetUseCase
├─ backtest/
│  ├─ RunBacktestUseCase
│  └─ GenerateReportUseCase
├─ community/
│  └─ GetCommunityMetricsUseCase
├─ ports/                 # 인터페이스(Port) 정의
│  ├─ MarketDataPort
│  ├─ BrokerPort
│  ├─ PortfolioPort
│  ├─ CommunityPort
│  └─ ClockPort
└─ dto/                   # 유즈케이스 입력/출력 DTO
```

원칙:
- usecase는 “흐름/규칙 적용”을 담당하고,
- 외부 연동은 Port(interface)로만 의존한다(구현은 infra).

### 3.3 modules/infra/persistence-mysql (DB 연동)

```text
com.yourorg.trade.infra.persistence
├─ jpa/
│  ├─ entity/
│  ├─ repository/
│  └─ mapper/           # domain <-> entity 변환
└─ adapter/
   ├─ MarketDataPortAdapter implements MarketDataPort
   ├─ PortfolioPortAdapter implements PortfolioPort
   └─ CommunityPortAdapter implements CommunityPort
```

### 3.4 modules/infra/market-kis (브로커 연동)

```text
com.yourorg.trade.infra.kis
├─ http/
│  ├─ KisRestClient
│  ├─ KisAuthTokenProvider  # 토큰 캐시/갱신
│  └─ RateLimiter/RetryPolicy
├─ ws/
│  ├─ KisWebSocketClient
│  └─ StreamHandler
└─ adapter/
   └─ BrokerPortAdapter implements BrokerPort
```

### 3.5 modules/web (REST API)

```text
com.yourorg.trade.web
├─ controller/      # Strategy/Backtest/Trading/Community
├─ request/
├─ response/
├─ mapper/          # web dto <-> usecase dto
└─ config/          # OpenAPI/Validation 등 최소 구성
```

### 3.6 apps/api-server (실행 조립)

- Spring Boot main
- 모듈 조합(빈 등록)
- 스케줄러 구동(장 시작/마감/배치)
- 프로파일 분리(dev/prod)

---

## 4. Python 영역(최소 범위)

```text
python/
├─ crawler/
│  └─ collect_allowed_sources.py     # 허용된 소스 수집
├─ sentiment/
│  └─ build_community_index.py       # 찬티/안티/버즈 지수 산출
└─ dashboard/
   └─ app.py                         # Streamlit: Java API 호출 또는 MySQL 조회
```

권장 연결:
- Python이 산출한 지수는 `community_metrics` 테이블로 적재하고,
- Java는 CommunityPort로 조회해서 “필터/가중치”로 사용한다.

---

## 5. 개발 순서(요약)

1) **Domain + Backtest** 먼저: 전략이 숫자로 검증 가능해야 한다.  
2) 그다음 **Execution + Risk + KIS(모의)**: 실시간 안정성을 확보한다.  
3) 마지막으로 Python 지수/대시보드: 운영 편의성/확률 보정.

---
