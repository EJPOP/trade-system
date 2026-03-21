# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Korean stock exchange (KRX + NXT) data collection, quant strategy, and automated trading system. Three main layers:
- **Java (Spring Boot)**: Fetches KRX OpenAPI data and stores it in MySQL
- **Python**: Reads DB data to compute quant strategies, web dashboard, broker integration, notifications, and macro regime analysis
- **Web Dashboard (FastAPI)**: SPA with 6 menu sections — 퀀트 전략, 매매(NXT/KRX), 포트폴리오, 시스템

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
python -m <module> --help                   # Verify module CLI
python -m tests.smoke_macro                 # Smoke test (US macro)
python -m tests.smoke_macro_kr              # Smoke test (KR macro)
```

### Web Dashboard (FastAPI)
```bash
cd python
python -m src.web                           # Run (port 8000, default)
python -m src.web --port 8080               # Custom port
```

### Daily Pipeline
```bash
cd python
python -m src.run_full_pipeline                          # Full pipeline (yesterday)
python -m src.run_full_pipeline --asOfDate 20260315      # Specific date
python -m src.run_full_pipeline --strategies turtle,vqm  # Specific strategies
python -m src.run_full_pipeline --dryRun                 # No Slack send
python -m src.run_full_pipeline --skipSync               # Skip KRX sync
python -m src.run_full_pipeline --skipDart               # Skip DART (quarterly, weekly enough)
python -m src.run_full_pipeline --skipSync --skipDart --dryRun  # Fast test
```

PowerShell alternative:
```powershell
cd C:\trade-system\python
.\run_daily_pipeline.ps1 -AsOfDate (Get-Date -Format 'yyyyMMdd')
```

### Multi-Account Portfolio
```bash
cd python
python -m src.run_account --account balance1 --asOfDate 20260315
python -m src.run_balance_pipeline --asOfDate 20260315              # All accounts
python -m src.run_balance_pipeline --accounts balance1,balance3     # Subset
```

### Monitoring
```bash
cd python
python -m src.monitoring.run_monitor --asOfDate 20260315
python -m src.monitoring.run_monitor --category data_freshness
python -m src.monitoring.run_monitor --dailySummary   # Slack send
```

## Tech Stack

- **Java 25**, Spring Boot 4.0.1, WebFlux (reactive), MyBatis 4.0.1, MySQL 8.x
- **Python 3.13+** (`C:\Program Files\Python313\python.exe`), FastAPI, runs via `python -m ...`, env vars in `python/.env`

## Architecture

### Java Layer (`src/main/java/top/tradesystem/`)

Reactive layered architecture with `Mono<T>` returns throughout:

- **`client/`** - `KrxOpenApiClient`: HTTP client for KRX API. MS949/UTF-8 charset fallback. WebClient 20MB buffer.
- **`controller/`** - REST controllers: daily trade (`/svc/apis/sto`), index prices (`/svc/apis/idx`), ticker master (`/internal/krx/tickers/master`).
- **`service/`** - `BaseSyncService` base with `Schedulers.boundedElastic()`. Range sync with configurable `delayMs`. Retry 429/5xx; skip 403.
- **`repository/`** - MyBatis mappers. Batch upserts via `ON DUPLICATE KEY UPDATE`.
- **`dto/`** - Java records. `KrxDailyTradeRow` all-String fields (safe for VARCHAR numeric columns).
- **`config/`** - `KrxProperties`, WebClient, Jackson config.

MyBatis XML mappers: `src/main/resources/mapper/*.xml`

### Python Layer (`python/src/`)

- **`web/`** - FastAPI SPA dashboard + broker integration. 11 API route modules + main `app.py` routes. See [Web Dashboard](#web-dashboard) section.
- **`strategies/`** - 17 quant strategies extending `BaseStrategy` ABC. `batch_runner.py` for in-process batch execution with ThreadPoolExecutor.
- **`daytrade/`** - Day trading engine: `core/`, `strategies/` (12 implementations), `scoring/`, `regime/`
- **`notifications/`** - Slack: `strategy/` (picks, all_strategies, strict_triplet, report10), `timeslot/` (pre_market, open_scanner, close_betting)
- **`balance_common/`** - Multi-account portfolio: models, runner (in-process + subprocess fallback), portfolio, db_store, risk_guard
- **`indicators/`** - Valuation metrics (PER, PBR, EV)
- **`macro/`** - US (FRED) / KR (ECOS) macro regime detection
- **`modules/`** - Hexagonal architecture: `domain/`, `usecase/`, `infra/persistence_mysql/`, `app/`
- **`us_market/`** - S&P500, OHLCV (yfinance), sector ETF
- **`news/`** - News scoring, Naver search, RSS, theme classification
- **`dart/`** - DART disclosure/financial data
- **`ops/`** - Data quality, paper trades, portfolio building, performance reports
- **`monitoring/`** - 5 categories: data_freshness, pipeline_health, strategy_perf, system_health, anomaly
- **`backtests/`** - Parameter optimization, backtesting
- **`utils/`** - `cli.py`, `logger.py`, `date_helpers.py`, `pandas_utils.py`, `constants.py`
- **`crawlers/`** - Naver Finance KRX crawler (API fallback)

### Web Dashboard (`python/src/web/`)

**API Route Modules (11개):** `api_market`, `api_market_global`, `api_market_news`, `api_monitor`, `api_sync`, `api_watchlist` (뉴스 키워드), `api_trade_watchlist` (매매 관심종목), `api_strategy`, `api_account_config`, `api_calendar`, `api_auto_trade` (자동매매)

**Main `app.py` routes:** `/api/broker/*` (KIS 주문/호가/잔고), `/api/broker/nxt-order` (NXT/SOR 주문), `/api/broker/prices` (일괄 현재가), `/api/broker/nxt-price/{code}` (NXT 시간외가), `/api/timeslot/*` (타임슬롯 스캐너), `/api/market-session` (KRX+NXT 세션), `/api/nxt-gap-scanner` (NXT 갭 스캔), `/api/nxt-gap-trade` (NXT 갭 매매), `/api/sync/naver/daily-trade` (네이버 당일종가 적재)

**`_ensure_real_account()`** — 시세 조회 API 호출 시 모의투자 계좌이면 실투자 계좌로 자동 전환 (VTS는 NXT/시간외 시세 미제공)

**Sidebar Menu (6 sections):**
- 공통: 대시보드, 시장현황, 종목조회, 뉴스, 캘린더, 관심종목
- 퀀트 전략: 전략추천, 프리마켓/장초반/종가배팅 스캐너
- 매매 [NXT][KRX]: 주문(KIS), 자동매매, 시그널로그, 주문이력
- 포트폴리오: 계좌종합, 개별계좌
- 시스템: 데이터동기화 (KRX Open API / 네이버 당일종가 / US / 밸류에이션), 모니터링
- 하단 퀵액션: 파이프라인, 자동매매

**Frontend JS Modules (14개):** `core.js` (API/모달/탭), `stock-modal.js` (주문모달/호가/차트), `strategy.js` (전략실행), `stock-chart.js` (OHLCV차트), `timeslot.js` (스캐너), `market.js` (시장현황), `news.js` (뉴스), `auto-trade.js` (자동매매UI), `watchlist-trade.js` (관심종목), `ops.js` (주문관리), `calendar.js`, `account.js`, `dashboard-view.js` (포트폴리오), `dashboard.js`

### Broker Integration (`web/broker/kis_client.py`)

한국투자증권(KIS) Open Trading API client:
- **NXT (Nextrade) 지원**: `place_order(venue="NXT"|"SOR"|"KRX"|"")` — venue별 tr_id/엔드포인트 자동 분기
- **세션 감지**: `get_market_session()` → KRX + NXT 듀얼 세션 + `best_venue` 추천 (NXT/SOR/KRX)
- **NXT 시간대**: pre(08:00-08:50), regular(09:00-15:20), post(15:30-20:00). NXT는 시장가(01) 미지원 → 지정가(00)만.
- **NXT 시세 (`get_nxt_price`)**: `FHKST01010200` API — `output2`에서 가격, `output1`에서 호가잔량 파싱. `antc_cnpr`(예상체결가) 우선, 없으면 `stck_prpr`.
- **일괄 시세**: `get_current_prices_batch()`, `get_nxt_prices_batch()` — ThreadPoolExecutor 병렬, 토큰 사전확보, 헤더 1회 생성
- **HTTP Session**: `requests.Session` — TCP/TLS 연결 재사용
- **계좌별 토큰 캐시**: `_token_cache_per_account`, 403 rate limit 시 62초 대기 재시도
- **모의투자 서버(VTS) 제한**: NXT/시간외 시세 조회 미지원 → `_ensure_real_account()`로 실투자 계좌 자동 전환

### Auto-Trade Engine (`web/auto_executor.py`)

- **`execute_signals()`** — DB 시그널 → KIS 주문 변환·실행. venue 자동감지 (NXT 세션이면 NXT 주문)
- **`monitor_stop_conditions()`** — 오픈 포지션 손절/익절/트레일링 체크 → 자동 매도. NXT 야간장이면 NXT 시세+NXT 주문
- **`execute_nxt_pre_market()`** — 프리마켓 전략 실행 → NXT 매수 + TP 매도 자동 등록 (account_config.take_profit_pct 사용)
- **`update_trailing_stops_live()`** — 고점 갱신 시 트레일링 스탑 상향
- **`reconcile_positions()`** — DB 포지션 vs KIS 브로커 잔고 정합성 검증
- **Exit 조건**: 1) `stop_price` 이하 → STOP_LOSS, 2) `trailing_stop_price` 이하 → TRAILING_STOP, 3) `take_profit_price` 이상 → TAKE_PROFIT
- **venue 감지 in monitor_stops**: NXT만 열려있으면 NXT 시세+NXT 지정가 매도, 양쪽 열리면 KRX 우선

### NXT 갭 매매 (`web/nxt_gap_scanner.py`)

- **`scan_nxt_gap()`** — KRX 종가 대비 NXT 시간외가 N%+ 상승 종목 스캔 (거래량 상위 → NXT 일괄 시세 → 갭 필터)
- **`execute_nxt_gap_trade()`** — 갭 매매 전략: 스캔 → NXT 매수(+1호가) → TP 매도(+4%) → SL(-2%) DB 등록. `monitor_stop_conditions()`에서 SL 자동 감시.

### 데이터 적재 (`api_sync.py` + `crawlers/`)

- **KRX Open API** (T+1): `POST /api/sync/krx/*` — Spring Boot 7777 필요, 전일 종가를 익일에 적재
- **네이버 크롤러** (당일): `POST /api/sync/naver/daily-trade?date=YYYYMMDD` — 15:30 이후 당일 종가 즉시 적재. `sync_krx_from_naver.py` (20워커 병렬 OHLCV 크롤링)

### Database (`quant` schema)

Primary tables:
- `krx_ticker_master` — PK: `code`
- `krx_daily_trade` — PK: `(bas_dd, isu_cd)`, date format `YYYYMMDD`
- `krx_index_daily_price` — PK: `(bas_dd, market, idx_nm)`
- `krx_daily_valuation` — Computed valuation metrics
- `dart_financials_snapshot` — DART financial statements

Application tables (auto-created):
- `account_config` — Per-account settings (budget, strategies, KIS credentials, `include_credit`, `is_primary`)
- `krx_latest_valuation` — Latest valuation snapshot (PK: `isu_cd`). Auto-refreshed on startup + after valuation update.
- `auto_execution_log` — Auto-trade signal execution log. Status: FILLED/FAILED/SKIPPED/REJECTED/DRY_RUN
- `trade_watchlist` — Trading watchlist items (UQ: `isu_cd`). Priority ordering, target prices, source tracking.
- `broker_order_log` — KIS broker order logging (success/failure per order)
- `timeslot_signal_log` — Timeslot execution results persistence
- `paper_trade_log`, `monitoring_check_log`, `pipeline_run_log`

Performance indexes (auto-created on startup):
- `idx_kdt_code_date` ON `krx_daily_trade (isu_cd, bas_dd)`
- `idx_kdv_isu_date_desc` ON `krx_daily_valuation (isu_cd, bas_dd DESC)`

DB scripts: `.reffiles/db-create-script/`, KRX API docs: `.reffiles/krx-openapi-guide/`

## Daily Pipeline Process

`python -m src.run_full_pipeline` runs Steps 1-5:

1. **KRX sync** → MySQL upsert (requires Spring Boot on port 7777)
2. **Data quality** → Row counts, table validation (advisory, non-blocking)
3. **Valuation + DART** → PER/PBR/EV metrics + financial statements → `refresh_latest_valuation()` auto-called after
4. **Strategy picks** → batch_runner pre-computes all picks (ThreadPoolExecutor) → 4-worker parallel notification subprocess
5. **Slack notify** → Per-strategy picks + Top 3 consensus

17 Strategies: turtle, vqm, lowvol, fvg, trend, flow, csmom, dualmom, tsmom, value, quality, riskparity, carry, pairs, reversal, volmanaged, sentdiv

## Configuration

- `src/main/resources/application.yml` — DB, KRX API, charset, logging
- `python/.env` — `DB_*`, `SLACK_*`, `DART_API_KEY`, `FRED_API_KEY`, `ECOS_API_KEY`, `KR_ECOS_*`, `KIS_*`

## Key Conventions

- Date format everywhere: `YYYYMMDD` string
- Venue parameter: `""` (auto/KRX legacy) | `"KRX"` | `"NXT"` | `"SOR"`. NXT는 시장가(01) 미지원.
- Numeric DB columns are `VARCHAR` in `krx_daily_trade` — `fetch_trade_window()` auto-CASTs to DECIMAL via `_apply_casts()`. `to_float_series()` skips if already numeric.
- Python imports: always absolute from `src` (e.g., `from src.modules...`), never relative root imports
- DB connections: `get_conn()` returns `_PooledConnection` — supports `with` statement (auto pool return). Use `with get_conn() as conn:` pattern everywhere.
- SQL safety: Parameterized queries (`%s`) for all values. Table names validated via whitelist (`_ALLOWED_TABLES`).
- New quant strategies must extend `BaseStrategy` ABC in `strategies/base.py`, register in `registry.py`
  - Implement: `name`, `description` properties + `build_picks(trade_df, args, port)` method
  - Helpers: `self.liquidity_filter(df, pct)`, `self.fetch_valuation(port, date, codes)`
- Strategy vectorization: Use `cumcount(ascending=False)` for `_pos` (0=last row), position-filtered groupby for metrics. Never use per-stock Python loops (`for isu_cd, g in df.groupby()`).
- KRX 호가 단위: `krxTickSize(price)` / `_tick_size(price)` (Python). 매수 +1호가, 매도 -1호가 지정가 패턴.
- 시그널 이력: `signal-history` API가 익일 시가/고가/종가 + 시그널가 대비 등락률(`next_open_pct` 등) 자동 JOIN 반환
- Day trading: `daytrade/{core,strategies,scoring,regime}/`
- Notifications: `notifications/{strategy,timeslot}/` (shared modules at root)
- MyBatis: `map-underscore-to-camel-case: false` — column names match DB exactly
- Architecture docs: `python/guide.md`, commands: `python/command.md`, setup: `python/setup.md`

## Performance Architecture

- **DB startup hooks**: `ensure_performance_indexes()` + `ensure_latest_valuation_table()` — idempotent, auto-run on web app + pipeline startup
- **Valuation snapshot**: `krx_latest_valuation` refreshed via `refresh_latest_valuation()` after every valuation update (prevents stale data)
- **SQL CAST**: `_apply_casts()` converts VARCHAR→DECIMAL in SELECT, eliminating Python string-to-float
- **Batch runner**: `batch_runner.run_batch_inprocess()` — loads trade data once, slices per-strategy lookback. Pure-compute strategies run in parallel (ThreadPoolExecutor); DB-dependent strategies run sequentially.
- **Pipeline parallelization**: 4-worker ThreadPoolExecutor for notification subprocess dispatch. `--skipDart` skips quarterly DART collection on daily runs.
- **Vectorized strategies**: All 23 strategies (17 main + 6 report10 subs) use `cumcount` + position-filtered groupby. Pairs uses `pivot().corr()` matrix.
- **In-process execution**: `picks.py` uses `batch_runner.run_single_inprocess()` with subprocess fallback. `balance_common/runner.py` uses shared trade data with in-process runner.
- **Connection pooling**: `_PooledConnection` wrapper on `get_conn()` — context manager support, auto pool return on `close()` or `with` exit.
- **HTTP Session reuse**: `kis_client.py` uses `requests.Session` for TCP/TLS connection reuse. Token pre-warming before batch parallel calls.

## Windows Environment Notes

- Shell: Git Bash (Unix syntax, forward slashes)
- Git Bash converts `/F`, `/PID` flags to filesystem paths — use `powershell.exe -Command "Stop-Process"` instead of `taskkill.exe`
- `__pycache__` can prevent code updates after edits — delete `.pyc` files and restart server when changes don't take effect
