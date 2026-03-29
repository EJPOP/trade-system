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

### 세계적 증권시스템 기준 — 서비스 분리 원칙

```
┌─ 프론트오피스 ────────────────────────────────────┐
│  주문접수 → OMS → EMS → 거래소/브로커 연동        │
│  실시간 시세 → 호가/캔들/스냅샷                    │
│  전략 엔진 → 시그널 생성                          │
├─ 미들오피스 ────────────────────────────────────────┤
│  실시간 리스크 → 주문검증 → 컴플라이언스           │
│  한도/증거금 관리 → 이상거래 감시                  │
├─ 백오피스 ────────────────────────────────────────┤
│  체결정산 → 원장 → 잔고 → 세금/대사/회계          │
├─ 플랫폼 ──────────────────────────────────────────┤
│  인증 → 감사로그 → 메시지버스 → 모니터링           │
│  데이터레이크 → 리포팅                             │
└──────────────────────────────────────────────────┘

핵심 원칙:
- 주문 실행 경로(hot path)와 조회/분석 경로 강하게 분리
- 주문, 리스크, 원장, 시세, 정산은 각각 독립 도메인
- 저지연 경로: 동기 + 메모리 캐시 + 이벤트 기반
- 체결 후 처리: 비동기 이벤트 기반
- 원장/체결: append-only, idempotency, audit trail
```

### 디렉터리 구조 (`python/src/`)

```
src/
  web/              # Channel — FastAPI controller only (API 파라미터 처리만)
  usecases/         # Application — OMS/EMS/RMS + 오케스트레이션 (15파일)
  stores/           # Infrastructure — SQL 전담 (17개 store 모듈)
  broker/           # Infrastructure — KIS adapter (IBroker 인터페이스)
  shared/           # Shared — 순수 유틸 (tick, cli, logger, date, pandas)
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
| Order Service | `usecases/oms.py`, `usecases/ems.py`, `usecases/rms.py` |
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

### pytest (unit — DB 불필요)
```bash
cd python
python -m pytest tests/unit/ -v             # 21 tests, 0.03s
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
| SQL 격리 | API/텔레그램 148건 → 0건, Store 17개 |
| 공용 Usecase | OMS/EMS/RMS + 전략/주문/계좌/관심종목 (15파일) |
| 모듈 분리 | Python 대형 파일 21→12개, JS 13→28개 |
| 패키지 통합 | 16개 → 13개 (services→usecases, utils→shared, tests→루트) |
| 디렉터리 이관 | stores, usecases, broker, shared, ingestion, research, domain |
| 백테스트 버그 | PCA 부호보정, 슬리피지, look-ahead, tick_above(-n) |
| DB 풀 안전성 | rollback 강제, autocommit 초기화 |
| 파이프라인 최적화 | 중복 계산 제거 (_picks_cache) |
| 전략 고도화 | 좀비필터, ATR손절, RSI다이버전스, 레짐게이팅 |
| Java 개선 | 입력검증 400핸들러, WebClient timeout, 연속403 감지, 부분적재 고착 수정, KRX 오류 변환 |
| 셀프테스트 | 12개 (서버 시작 자동) + pytest 21개 (unit) |
| Shim 정리 | 91개 → 22개 (75% 감소) |
| 삭제 | 3,316줄+ (죽은코드/중복) |

### 🔄 잔여 사항

| 항목 | 현재 상태 |
|------|----------|
| shim | 0개 (전수 제거 완료) |
| 500줄+ | 12개 (전부 비실거래 — 백테스트/크롤러) |
| load_dotenv | 엔트리포인트만 유지 |

### 📋 미래 과제

- CQRS 읽기 모델 (조회 전용 뷰)
- 메시지 큐 기반 비동기 주문 처리
- Redis 캐시 레이어

## Shim 구조

Shim 전수 제거 완료 (91개 → 0개). 모든 import가 실제 구현 위치를 직접 참조.

## Performance Architecture

- **DB startup hooks**: `ensure_performance_indexes()` + `ensure_latest_valuation_table()`
- **Batch runner**: 데이터 1회 로드, ThreadPoolExecutor 병렬, `_picks_cache` 중복 방지
- **Connection pooling**: `_PooledConnection` — with문, rollback 자동, autocommit 초기화
- **HTTP Session**: `requests.Session` — TCP/TLS 재사용, 토큰 사전 확보
- **Numeric columns**: krx_daily_trade 숫자 컬럼 DECIMAL/BIGINT (VARCHAR CAST 불필요)
- **Vectorized strategies**: `cumcount` + position-filtered groupby. iterrows 최소화
- **Parquet cache**: `data/cache/krx_daily_trade.parquet` — 백테스트 53초→1초 (파이프라인 자동 갱신)
- **Factor cache**: `krx_daily_factor_cache` 테이블 — MA5/MA20/MA60/RSI14 사전 계산 (60일분)
- **KIS token file cache**: `.kis_token_cache.json` — 서버 재시작 시 토큰 재발급 불필요
- **Portal preload**: 서버 시작 시 글로벌 포털 데이터 백그라운드 프리로드 (대시보드 즉시 응답)

## Database (`quant` schema)

Primary: `krx_daily_trade` (PK: bas_dd+isu_cd), `krx_ticker_master`, `krx_index_daily_price`, `krx_daily_valuation`, `dart_financials_snapshot`

Application (auto-created): `account_config`, `krx_latest_valuation`, `auto_execution_log`, `trade_watchlist`, `broker_order_log`, `timeslot_signal_log`, `monitoring_check_log`, `pipeline_run_log`

Sector mapping: `global_sector` (11), `unified_sector` (28), `sector_mapping_rule` (209 rules)

Cache: `krx_daily_factor_cache` (MA/RSI 사전계산), `krx_latest_valuation` (최신 스냅샷)

### DB 최적화 (✅ 완료)

| 항목 | 상태 | 내용 |
|------|------|------|
| VARCHAR→DECIMAL | ✅ 완료 | krx_daily_trade 10개 숫자 컬럼 DECIMAL/BIGINT 변환 |
| buffer_pool 12GB | ✅ 완료 | RAM 32GB 기준 최적, hit rate 98.9% |
| SSD 최적화 | ✅ 완료 | io_capacity=2000, doublewrite=OFF, read_io_threads=8 |
| 메모리 버퍼 | ✅ 완료 | tmp_table=256MB, max_heap=256MB, read_buffer=4MB |
| 커버링 인덱스 | ✅ 완료 | idx_kdt_strategy, idx_kdt_backtest (전략/백테스트 쿼리 인덱스 전용) |
| 파티셔닝 | ✅ 완료 | krx_daily_trade(13), krx_daily_valuation(8), us_daily_trade(8) |
| Collation 통일 | ✅ 완료 | utf8mb4_0900_ai_ci 36개 / unicode_ci 1개 (JOIN 시 변환 불필요) |
| 종목코드 통일 | ✅ 완료 | code/isu_cd/stock_code 전부 VARCHAR(16) |
| 중복 인덱스 제거 | ✅ 완료 | 118→99개 (중복 9개 + 불필요 10개 제거) |
| 중복 테이블 제거 | ✅ 완료 | krx_daily_price(2.6GB) → _backup, 빈 테이블 8개 → _unused |
| 팩터 캐시 테이블 | ✅ 완료 | krx_daily_factor_cache (MA5/20/60, RSI14, 60일분, 파이프라인 자동) |
| Parquet 캐시 | ✅ 완료 | data/cache/krx_daily_trade.parquet (178MB, 백테스트 50x 고속화) |
| KIS 토큰 파일 캐시 | ✅ 완료 | .kis_token_cache.json (서버 재시작 유지, 65초 발급 간격) |
| 밸류에이션 스냅샷 | ✅ 완료 | krx_latest_valuation (PK: isu_cd, 서버 시작 시 갱신) |
| DB 풀 rollback | ✅ 완료 | __exit__에서 rollback 강제 + return_conn에서 autocommit 초기화 |

### MySQL 설정 (my.ini — 32GB RAM 최적)

```
innodb_buffer_pool_size=12G
innodb_io_capacity=2000
innodb_io_capacity_max=4000
innodb_read_io_threads=8
innodb_doublewrite=OFF
innodb_flush_log_at_trx_commit=2
innodb_redo_log_capacity=512M
tmp_table_size=256M
max_heap_table_size=256M
sort_buffer_size=4M
join_buffer_size=4M
read_buffer_size=4M
thread_cache_size=32
```

### Java 개선 사항

### Java 개선 사항

| 항목 | 파일 | 내용 |
|------|------|------|
| 입력 검증 | KrxDailyTradeController | YYYYMMDD 정규식 검증 → 400 응답 |
| 400 핸들러 | GlobalExceptionHandler | IllegalArgument + DateTimeParse → 400 |
| 내부 메시지 노출 방지 | GlobalExceptionHandler | Exception → "Internal server error" 고정 |
| fetchDailyPrice 별칭 삭제 | KrxOpenApiClient | 혼동 유발 미사용 메서드 제거 |

## Windows Environment

- Shell: Git Bash (Unix syntax)
- `__pycache__` 삭제 후 서버 재시작 when changes don't take effect
- `powershell.exe -Command "Stop-Process"` for process management
