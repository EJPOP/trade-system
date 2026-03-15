# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Korean stock exchange (KRX) data collection and quant strategy system. Two main layers:
- **Java (Spring Boot)**: Fetches KRX OpenAPI data and stores it in MySQL
- **Python**: Reads DB data to compute quant strategies, web dashboard, broker integration, notifications, and macro regime analysis

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
python -m src.run_balance_pipeline --asOfDate 20260315              # All 4 accounts
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

- **`web/`** - FastAPI SPA dashboard + broker integration (`broker/kis_client.py`). 10 API route modules (`api_market.py`, `api_strategy.py`, `api_account_config.py`, etc.)
- **`strategies/`** - 17 quant strategies extending `BaseStrategy` ABC. `batch_runner.py` for in-process batch execution with ThreadPoolExecutor.
- **`daytrade/`** - Day trading engine: `core/`, `strategies/` (12 implementations), `scoring/`, `regime/`
- **`notifications/`** - Slack: `strategy/` (picks, all_strategies, strict_triplet, report10), `timeslot/` (pre_market, open_scanner, close_betting)
- **`balance_common/`** - Multi-account portfolio (balance1~4): models, runner (in-process + subprocess fallback), portfolio, db_store, risk_guard
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
- Numeric DB columns are `VARCHAR` in `krx_daily_trade` — `fetch_trade_window()` auto-CASTs to DECIMAL via `_apply_casts()`. `to_float_series()` skips if already numeric.
- Python imports: always absolute from `src` (e.g., `from src.modules...`), never relative root imports
- DB connections: `get_conn()` returns `_PooledConnection` — supports `with` statement (auto pool return). Use `with get_conn() as conn:` pattern everywhere.
- SQL safety: Parameterized queries (`%s`) for all values. Table names validated via whitelist (`_ALLOWED_TABLES`).
- New quant strategies must extend `BaseStrategy` ABC in `strategies/base.py`, register in `registry.py`
  - Implement: `name`, `description` properties + `build_picks(trade_df, args, port)` method
  - Helpers: `self.liquidity_filter(df, pct)`, `self.fetch_valuation(port, date, codes)`
- Strategy vectorization: Use `cumcount(ascending=False)` for `_pos` (0=last row), position-filtered groupby for metrics. Never use per-stock Python loops (`for isu_cd, g in df.groupby()`).
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

## Windows Environment Notes

- Shell: Git Bash (Unix syntax, forward slashes)
- Git Bash converts `/F`, `/PID` flags to filesystem paths — use `powershell.exe -Command "Stop-Process"` instead of `taskkill.exe`
- `__pycache__` can prevent code updates after edits — delete `.pyc` files and restart server when changes don't take effect
