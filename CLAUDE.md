# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Korean stock exchange (KRX + NXT) data collection, quant strategy, and automated trading system.
- **Java (Spring Boot)**: Fetches KRX OpenAPI data → MySQL
- **Python**: Quant strategies, web dashboard, broker integration, notifications, macro regime
- **Web Dashboard (FastAPI)**: SPA with 6 menu sections

## Architecture — 증권시스템 4계층 구조

```
┌─────────────────────────────────────────────────┐
│  Channel (채널)                                  │
│  FastAPI, CLI, Telegram, Slack, Scheduler        │
├─────────────────────────────────────────────────┤
│  Application (유스케이스)                         │
│  전략실행, 주문실행, 관심종목관리, 잔고조회       │
├─────────────────────────────────────────────────┤
│  Domain (도메인)                                 │
│  전략규칙, 포트폴리오, 리스크, 호가계산          │
├─────────────────────────────────────────────────┤
│  Infrastructure (인프라)                          │
│  Store(SQL), Broker(KIS), Ingestion, Notification│
└─────────────────────────────────────────────────┘
```

### 디렉터리 구조 (`python/src/`)

```
src/
  web/              # Channel — FastAPI controller only (API 파라미터 처리만)
  usecases/         # Application — 공용 오케스트레이션 (web/telegram/pipeline 공유)
  stores/           # Infrastructure — SQL 전담 (16개 store 모듈)
  broker/           # Infrastructure — KIS adapter (IBroker 인터페이스)
  services/         # Infrastructure — order_service
  shared/           # Shared — 순수 유틸 (tick_utils)
  strategies/       # Domain — 종목 선정 (19개 BaseStrategy + MFZ 인터페이스)
  daytrade/         # Domain — 장중 매매 도메인
  domain/           # Domain — 포트폴리오/레저/리스크/플로우
  ingestion/        # Infrastructure — 데이터 수집 (crawlers/dart/us/global)
  notifications/    # Infrastructure — 알림 발송 (Slack/Telegram)
  monitoring/       # Infrastructure — 시스템 점검
  research/         # Research — 백테스트/분석/매크로/뉴스 (실거래 경로 아님)
  ops/              # Infrastructure — DB 연결, 스케줄러
```

### 경계 규칙 (Boundary Rules)

| 규칙 | 상태 |
|------|------|
| web/api에 SQL 직접 호출 금지 | ✅ 0건 |
| stores에 HTTP/Telegram/Slack 금지 | ✅ |
| strategies에 주문 실행 금지 | ✅ |
| usecases가 stores/broker/notifications 조합 | ✅ |
| pymysql.connect()는 ops/db.py 외부 금지 | ✅ 0건 |
| subprocess는 외부 프로세스만 (내부는 import) | ✅ |
| 배치와 웹이 같은 usecase 공유 | ✅ |

### ABC 인터페이스 (3개, 8구현체)

| 인터페이스 | 위치 | 구현체 |
|-----------|------|--------|
| IStockAnalyzer, IGrader, IHealthFilter | `strategies/analyzer_interface.py` | MfzAnalyzer, MfzGrader, DartHealthFilter |
| IBroker (Session/Price/Order/Account) | `broker/broker_interface.py` | KISBroker |
| ITelegramCommandGroup | `web/telegram_interface.py` | Query/Trade/Analysis/SyncCommands |

### 증권시스템 서비스 매핑 (13개)

| 서비스 | 위치 |
|--------|------|
| Order Service | `usecases/orders.py`, `services/order_service.py` |
| Execution Gateway | `broker/kis_broker.py`, `broker/_kis_order.py` |
| Market Data | `broker/_kis_price.py`, `stores/market_store.py` |
| Portfolio/Position | `usecases/accounts.py`, `domain/portfolio/` |
| Risk Service | `domain/risk/guard.py` |
| Reference Data | `stores/strategy_store.py`, `stores/market_data_store.py` |
| Strategy Engine | `usecases/run_strategy.py`, `strategies/base.py` |
| Signal Service | `stores/signal_store.py`, `stores/execution_store.py` |
| Compliance (경고/과열) | `stores/market_data_store_naver.py` |
| Batch/Post-Trade | `run_full_pipeline.py`, `pipeline_*.py` |
| Notification | `notifications/`, `web/telegram_bot.py` |
| Monitoring | `monitoring/`, `stores/monitor_store.py` |
| Data Ingestion | `ingestion/crawlers/`, `ingestion/dart/`, `ingestion/us_market/` |

### 셀프테스트 (서버 시작 시 자동, 12/12)

`web/startup_test.py` — MFZ인터페이스, 등급일관성, DB연결, 브로커인터페이스, 브로커호환, 주문서비스, 텔레그램, 텔레그램인터페이스, 전략레지스트리, 전략일관성, Store/Usecase, SQL격리

## Build & Run Commands

### Java (Spring Boot)
```bash
./gradlew compileJava -q     # Compile
./gradlew bootRun             # Run (port 7777)
./gradlew test                # Tests (JUnit 5)
```

### Python (`python/` directory)
```bash
cd python
python -m compileall src                    # Syntax check
python -m src.web                           # Web server (port 8000)
python -m src.web --port 8080               # Custom port
python -m src.run_full_pipeline             # Daily pipeline
python -m src.run_full_pipeline --dryRun    # No Slack
python -m src.run_full_pipeline --skipSync --skipDart --dryRun  # Fast test
```

### Selftest
```bash
cd python
python -c "from src.web.startup_test import run_startup_tests; run_startup_tests()"
```

### Multi-Account Portfolio
```bash
cd python
python -m src.run_account --account balance1 --asOfDate 20260315
python -m src.run_balance_pipeline --asOfDate 20260315
```

### Monitoring
```bash
cd python
python -m src.monitoring.run_monitor --asOfDate 20260315
python -m src.monitoring.run_monitor --dailySummary
```

## Tech Stack

- **Java 25**, Spring Boot 4.0.1, WebFlux, MyBatis 4.0.1, MySQL 8.x
- **Python 3.13+**, FastAPI, runs via `python -m ...`, env vars in `python/.env`

## Key Conventions

- Date format: `YYYYMMDD` string everywhere
- Venue: `""` | `"KRX"` | `"NXT"` | `"SOR"`. NXT는 시장가(01) 미지원
- Python imports: absolute from `src` (e.g., `from src.stores.market_store import ...`)
- DB: `with get_conn() as conn:` 패턴. rollback 자동. autocommit 초기화
- SQL: 파라미터화(`%s`). 테이블명 화이트리스트. **store 모듈에서만 SQL 허용**
- New strategies: `BaseStrategy` ABC 상속, `registry.py` 등록
- Tick size: `tick_above(price, n)` — n>0 위, n<0 아래 (negative n 지원)
- 19 Strategies: turtle, vqm, lowvol, fvg, trend, flow, csmom, dualmom, tsmom, value, quality, riskparity, carry, pairs, reversal, volmanaged, sentdiv, mfzscore, swing

## 리팩토링 진행 현황

### ✅ 완료

| 항목 | 내용 |
|------|------|
| ABC 인터페이스 | 3개 (전략/브로커/텔레그램), 8 구현체 |
| SQL 격리 | API/텔레그램 148건 → 0건, Store 16개 |
| 공용 Usecase | 4개 (전략/주문/계좌/관심종목) |
| 모듈 분리 | Python 대형 파일 21→12개, JS 13→28개 |
| 디렉터리 이관 | stores, usecases, broker, ingestion, research, domain |
| 백테스트 버그 | PCA 부호보정, 슬리피지, look-ahead, tick_above(-n) |
| DB 풀 안전성 | rollback 강제, autocommit 초기화 |
| 파이프라인 최적화 | 중복 계산 제거 (_picks_cache) |
| 전략 고도화 | 좀비필터, ATR손절, RSI다이버전스, 레짐게이팅 |
| Java 개선 | 입력검증 400핸들러, WebClient timeout, 연속403 감지 |
| 셀프테스트 | 12개 (서버 시작 자동) |
| 삭제 | 3,316줄 (죽은코드/중복) |

### 🔄 점진적 진행 중

| 항목 | 현재 상태 | 다음 단계 |
|------|----------|----------|
| shim 정리 | broker/services/shared는 역방향 shim | 실구현 이동 후 shim 제거 |
| 500줄+ 잔여 | 12개 (모두 비실거래 경로) | 필요 시 분리 |
| load_dotenv | 서비스 내부 4곳 | 엔트리포인트 집중 |
| VARCHAR→DECIMAL | DB 세션에서 진행 중 | 스키마 마이그레이션 |

### 📋 미래 과제

- pytest + dev dependency 체계
- unit/integration/smoke 테스트 분리
- CQRS 읽기 모델 (조회 전용 뷰)
- 메시지 큐 기반 비동기 주문 처리
- Redis 캐시 레이어

## Shim 구조 (하위 호환)

```
기존 경로 (유지)              →  실제 구현 위치
─────────────────────        ──────────────
src.web.usecases.*           →  src.usecases.*
src.web.stores.*             →  src.stores.*
src.balance_common.*         →  src.domain.portfolio/ledger/risk/*
src.modules.domain.*         →  src.domain.flow.*
src.modules.usecase.*        →  src.usecases.*

새 공용 경로 (추가)           →  실제 구현 위치 (아직 web/ 내부)
─────────────────────        ──────────────
src.broker.*                 →  src.web.broker.*
src.services.*               →  src.web.services.*
src.shared.*                 →  src.web.services.tick_utils
src.ingestion.*              →  src.crawlers/dart/us_market/global_market
src.research.*               →  src.backtests/analysis/indicators/macro/news
```

## Performance Architecture

- **DB startup hooks**: `ensure_performance_indexes()` + `ensure_latest_valuation_table()`
- **Batch runner**: 데이터 1회 로드, ThreadPoolExecutor 병렬, `_picks_cache` 중복 방지
- **Connection pooling**: `_PooledConnection` — with문, rollback 자동, autocommit 초기화
- **HTTP Session**: `requests.Session` — TCP/TLS 재사용, 토큰 사전 확보
- **SQL CAST**: VARCHAR→DECIMAL in SELECT
- **Vectorized strategies**: `cumcount` + position-filtered groupby. iterrows 최소화

## Database (`quant` schema)

Primary: `krx_daily_trade` (PK: bas_dd+isu_cd), `krx_ticker_master`, `krx_index_daily_price`, `krx_daily_valuation`, `dart_financials_snapshot`

Application (auto-created): `account_config`, `krx_latest_valuation`, `auto_execution_log`, `trade_watchlist`, `broker_order_log`, `timeslot_signal_log`, `paper_trade_log`, `monitoring_check_log`, `pipeline_run_log`

## Windows Environment

- Shell: Git Bash (Unix syntax)
- `__pycache__` 삭제 후 서버 재시작 when changes don't take effect
- `powershell.exe -Command "Stop-Process"` for process management
