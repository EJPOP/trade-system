# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Korean stock exchange (KRX) data collection and quant strategy system. Two main layers:
- **Java (Spring Boot)**: Fetches KRX OpenAPI data and stores it in MySQL
- **Python**: Reads DB data to compute quant strategies, notifications, and macro regime analysis

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

Daily pipeline (PowerShell):
```powershell
cd C:\trade-system\python
.\run_daily_pipeline.ps1 -AsOfDate (Get-Date -Format 'yyyyMMdd')
```

## Tech Stack

- **Java 25**, Spring Boot 4.0.1, WebFlux (reactive), MyBatis 4.0.1, MySQL 8.x
- **Python 3.13+**, runs via `python -m ...`, env vars in `python/.env`

## Architecture

### Java Layer (`src/main/java/top/tradesystem/`)

Reactive layered architecture with `Mono<T>` returns throughout:

- **`client/`** - `KrxOpenApiClient`: HTTP client for KRX API. Handles MS949/UTF-8 charset fallback for Korean legacy encodings. Uses WebClient with 20MB max in-memory buffer.
- **`controller/`** - REST controllers for daily trade (`/svc/apis/sto`), index prices (`/svc/apis/idx`), and ticker master (`/internal/krx/tickers/master`). All support single-date and range sync endpoints.
- **`service/`** - Business logic with `Schedulers.boundedElastic()` for blocking DB calls. Range sync loops with configurable `delayMs` to prevent API throttling. Retry logic for 429/5xx errors; 403 is skipped.
- **`repository/`** - MyBatis mapper interfaces. Batch upserts use `ON DUPLICATE KEY UPDATE`.
- **`dto/`** - Java records for request/response immutability.
- **`config/`** - Properties (`KrxProperties`), WebClient, Jackson config.

MyBatis XML mappers: `src/main/resources/mapper/*.xml`

### Python Layer (`python/src/`)

- **`strategies/`** - 17 quant strategy modules, all extending `BaseStrategy` ABC (`base.py`). Shared CLI/fetch/output in base; each strategy implements only `build_picks()`. Registry in `registry.py`.
- **`daytrade/`** - Day trading engine with 4 sub-packages:
  - `core/` - engine, models, data_loader, base, indicators
  - `strategies/` - 12 strategy implementations (orb, vwap_pullback, ema_cross, etc.)
  - `scoring/` - daily_scorer, factor_scorer, combined_scorer, factors
  - `regime/` - market regime detection, sector_map
- **`notifications/`** - Slack notifications with 2 sub-packages:
  - `strategy/` - picks, all_strategies, strict_triplet, report10
  - `timeslot/` - pre_market, open_scanner, close_betting, orchestrator
  - Root: shared modules (slack_send_bot, formatters, db_helpers, shared_fetchers)
- **`indicators/`** - Valuation metric computation from DB data
- **`macro/`** - US (FRED) and KR (ECOS) macro regime detection
- **`modules/`** - Hexagonal architecture core: `domain/` (models, ports, flow), `usecase/`, `infra/persistence_mysql/`, `app/`
- **`dart/`** - DART disclosure/financial data collection
- **`ops/`** - Data quality checks, paper trades, portfolio building, performance reports
- **`monitoring/`** - System monitoring: data freshness, pipeline health, strategy performance, anomaly detection
- **`backtests/`** - Parameter optimization and backtesting
- **`utils/`** - Shared utilities: `cli.py` (bootstrap), `logger.py`, `date_helpers.py`, `pandas_utils.py`, `constants.py`
- **`balance_common/`** - Portfolio account shared runner, models, db_store
- **`crawlers/`** - Naver Finance KRX data crawler (KRX API fallback)

### Database (`quant` schema)

Three primary tables:
- `krx_ticker_master` — PK: `code`
- `krx_daily_trade` — PK: `(bas_dd, isu_cd)`, date format `YYYYMMDD`
- `krx_index_daily_price` — PK: `(bas_dd, market, idx_nm)`

DB scripts in `.reffiles/db-create-script/`, KRX OpenAPI docs in `.reffiles/krx-openapi-guide/`.

## Daily Pipeline Process (End-to-End)

전체 프로세스: **KRX 데이터 수집/적재 → 17개 전략 종목 추천 → Slack 알림 전송**

```
┌─────────────────────────────────────────────────────────────┐
│  Step 1. KRX OpenAPI 데이터 수집 & DB 적재 (Java/Spring Boot)  │
│                                                             │
│  Spring Boot (port 7777) 가 KRX OpenAPI를 호출하여             │
│  MySQL quant DB에 upsert                                     │
│                                                             │
│  1-1. 종목 마스터 (krx_ticker_master)                          │
│       POST /internal/krx/tickers/master/sync                │
│  1-2. 종목 일일 거래 (krx_daily_trade)                         │
│       POST /svc/apis/sto/sync-range                         │
│  1-3. 지수 일일 시세 (krx_index_daily_price)                   │
│       POST /svc/apis/idx/sync-range                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 2. 데이터 품질 검증 (Python)                              │
│                                                             │
│  python -m src.ops.run_data_quality_checks                  │
│  - krx_daily_trade 행 수 >= 2000                             │
│  - krx_index_daily_price 행 수 확인                           │
│  - krx_ticker_master 비어있지 않은지 확인                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 3. 퀀트 전략 실행 & 종목 추천 (Python)                     │
│                                                             │
│  17개 전략 모듈이 DB 데이터를 읽어 종목 선정 (JSON 출력)           │
│  - turtle     : 터틀 트레이더                                 │
│  - vqm        : 가치·퀄리티·모멘텀                             │
│  - lowvol     : 저변동 추세                                   │
│  - fvg        : 페어밸류갭                                    │
│  - trend      : 추세 추종                                     │
│  - flow       : 수급 모멘텀                                   │
│  - csmom      : 크로스섹션 모멘텀                              │
│  - dualmom    : 듀얼 모멘텀 (절대+상대+매크로레짐)               │
│  - tsmom      : 시계열 모멘텀 (샤프비율)                        │
│  - value      : 멀티팩터 밸류 (PER/PBR/EV)                    │
│  - quality    : 퀄리티 (ROE/EBITDA/안정성)                     │
│  - riskparity : 리스크패리티 (역변동성)                         │
│  - carry      : 캐리 (이익/장부 수익률)                         │
│  - pairs      : 페어트레이딩 (Z-score)                        │
│  - reversal   : 단기반전 (주간 역추세)                          │
│  - volmanaged : 변동성조절 모멘텀 (시장레짐)                     │
│  - sentdiv   : 심리 다이버전스                                   │
│                                                             │
│  각 전략 → strict gate 필터 → 매수/매도/손절가 산출              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 4. Slack 알림 전송 (Python)                              │
│                                                             │
│  run_notify_strategy_picks_slack 이 각 전략별로:               │
│  - 엄격 매수 후보 종목 + 매매가이드 + 보조지표를 포맷           │
│  - Slack chat.postMessage API로 전송                         │
│                                                             │
│  필수 환경변수: SLACK_BOT_TOKEN, SLACK_CHANNEL_ID             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 5. 전략 종합 TOP 3 (Python)                              │
│                                                             │
│  전체 전략의 strict-gated 종목을 합산하여 최상위 3종목 선정      │
│  - 합산점수 = 추천전략수 × 평균정규화점수                        │
│  - 🥇🥈🥉 메달 표시로 Slack 별도 전송                          │
└─────────────────────────────────────────────────────────────┘
```

### Full Pipeline 실행 (Python 단일 프로그램)

`python/src/run_full_pipeline.py` — 위 Step 1~5를 순차 실행하는 통합 프로그램.
Spring Boot 앱이 실행 중이어야 Step 1이 동작함.

```bash
cd python

# 어제 날짜 기준 전체 파이프라인 (기본값)
python -m src.run_full_pipeline

# 특정 날짜 지정
python -m src.run_full_pipeline --asOfDate 20260311

# 특정 전략만
python -m src.run_full_pipeline --strategies turtle,vqm,csmom

# Slack 미전송 (dry run)
python -m src.run_full_pipeline --dryRun

# KRX sync 스킵 (이미 적재된 경우)
python -m src.run_full_pipeline --skipSync

# 품질 검증 스킵
python -m src.run_full_pipeline --skipQuality

# 실패 시 Slack 알림
python -m src.run_full_pipeline --notifyOnFailure
```

### PowerShell 파이프라인 (기존)

```powershell
cd C:\trade-system\python
.\run_daily_pipeline.ps1 -AsOfDate (Get-Date -Format 'yyyyMMdd')
```

## Configuration

- `src/main/resources/application.yml` — DB connection, KRX API base URL, auth key, charset, logging
- `python/.env` — `DB_*`, `SLACK_*`, `DART_API_KEY`, `FRED_API_KEY`, `ECOS_API_KEY`, `KR_ECOS_*`

### Monitoring System (`python/src/monitoring/`)

5개 점검 카테고리로 시스템 전체를 모니터링:

| 카테고리 | 모듈 | 점검 내용 |
|---------|------|---------|
| DATA_FRESHNESS | `check_data_freshness.py` | 테이블별 최신 날짜, 행 수, US 데이터 신선도 |
| PIPELINE_HEALTH | `check_pipeline_health.py` | 파이프라인 실행 기록, 실패 스텝, 실행 추이 |
| STRATEGY_PERF | `check_strategy_perf.py` | 수익률, 승률, MDD (paper_trade_log 기반) |
| SYSTEM_HEALTH | `check_system_health.py` | DB 연결, API 가용성, 디스크, 테이블 크기 |
| ANOMALY | `check_anomaly.py` | 중복 데이터, 비정상 가격, 거래량 급변, 종목 수 변동 |

```bash
# 전체 점검
python -m src.monitoring.run_monitor --asOfDate 20260314

# 특정 카테고리만
python -m src.monitoring.run_monitor --category data_freshness

# JSON 출력
python -m src.monitoring.run_monitor --json --dryRun

# 일일 요약 Slack 전송
python -m src.monitoring.run_monitor --dailySummary
```

DB 테이블:
- `monitoring_check_log` — 점검 결과 이력 (자동 생성)
- `pipeline_run_log` — 파이프라인 스텝 실행 기록 (자동 생성)

## Key Conventions

- Date format everywhere: `YYYYMMDD` string
- Numeric DB columns may be `VARCHAR` — safe conversion required before calculation (Python layer)
- Python imports: always absolute from `src` (e.g., `from src.modules...`), never relative root imports
- New quant strategies must extend `BaseStrategy` ABC in `strategies/base.py`
- Day trading sub-packages: `daytrade/{core,strategies,scoring,regime}/`
- Notification sub-packages: `notifications/{strategy,timeslot}/` (shared modules at root)
- MyBatis: `map-underscore-to-camel-case: false` — column names match DB exactly
- Root directory kept clean: reference files go in `.reffiles/`, IDE/build artifacts excluded from git
- Architecture docs: `python/guide.md`, operational commands: `python/command.md`
