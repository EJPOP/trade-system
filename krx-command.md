# KRX 명령어 가이드

## 1. from-to DB 저장 (최우선)
```powershell
cd C:\trade-system

# 1) 티커 마스터 저장(기간)
irm -Method Post "http://localhost:7777/internal/krx/tickers/master/sync-range?from=20250102&to=20251231&market=ALL&delayMs=300"

# 2) 종목 일일 거래 저장(기간)
irm -Method Post "http://localhost:7777/svc/apis/sto/sync-range?from=20250102&to=20251231&market=ALL&delayMs=300"

# 3) 지수 일일 시세 저장(기간)
irm -Method Post "http://localhost:7777/svc/apis/idx/sync-range?from=20250102&to=20251231&market=ALL&delayMs=300"
```
- `delayMs`는 일자별 호출 지연(ms)입니다. 과호출 방지용으로 `300~500` 권장.
- 날짜 형식: `yyyyMMdd`

## 2. Daily Batch (상단 운영 명령)
```powershell
cd C:\trade-system\python

# 일반 실행
.\run_daily_pipeline.ps1 -AsOfDate 20260220

# 재시도/실패 알림 포함
.\run_daily_pipeline.ps1 -AsOfDate 20260220 -RetryCount 3 -RetryDelaySec 10 -NotifyOnFailure

# 옵션
.\run_daily_pipeline.ps1 -AsOfDate 20260220 -SkipNotify
.\run_daily_pipeline.ps1 -AsOfDate 20260220 -SkipValuation
.\run_daily_pipeline.ps1 -AsOfDate 20260220 -SkipKrxSync
```

## 3. 저장 후 DB 건수 확인
```sql
USE quant;
SELECT COUNT(*) AS cnt FROM krx_ticker_master;
SELECT COUNT(*) AS cnt FROM krx_daily_trade WHERE bas_dd BETWEEN '20250102' AND '20251231';
SELECT COUNT(*) AS cnt FROM krx_index_daily_price WHERE bas_dd BETWEEN '20250102' AND '20251231';
```

## 4. 단일일자 DB 저장
```powershell
# 티커 마스터
irm -Method Post "http://localhost:7777/internal/krx/tickers/master/sync?basDd=20260220&market=ALL"

# 종목 일일 거래
irm -Method Post "http://localhost:7777/svc/apis/sto/sync-range?from=20260220&to=20260220&market=ALL&delayMs=300"

# 지수 일일 시세
irm -Method Post "http://localhost:7777/svc/apis/idx/sync-range?from=20260220&to=20260220&market=ALL&delayMs=300"
```

## 5. 조회 전용 (DB 저장 안 함)
```powershell
# idx 조회
irm -Method Post "http://localhost:7777/svc/apis/idx/kospi_dd_trd" -ContentType "application/json" -Body '{"basDd":"20260220"}'
irm -Method Post "http://localhost:7777/svc/apis/idx/kosdaq_dd_trd" -ContentType "application/json" -Body '{"basDd":"20260220"}'

# sto 조회
irm -Method Post "http://localhost:7777/svc/apis/sto/stk_bydd_trd" -ContentType "application/json" -Body '{"basDd":"20260220"}'
irm -Method Post "http://localhost:7777/svc/apis/sto/ksq_bydd_trd" -ContentType "application/json" -Body '{"basDd":"20260220"}'
irm -Method Post "http://localhost:7777/svc/apis/sto/stk_isu_base_info" -ContentType "application/json" -Body '{"basDd":"20260220"}'
irm -Method Post "http://localhost:7777/svc/apis/sto/ksq_isu_base_info" -ContentType "application/json" -Body '{"basDd":"20260220"}'
irm -Method Post "http://localhost:7777/svc/apis/sto/isu_base_info/range?from=20260102&to=20260221&market=ALL&delayMs=300"
```

## 6. 부수 정보 (하단)
- 앱 실행
```powershell
cd C:\trade-system
.\gradlew compileJava -q
.\gradlew bootRun
```
- 서버 주소: `http://localhost:7777`
- `market` 허용값: `KOSPI`, `KOSDAQ`, `ALL`
- `delayMs` 기본값: `0`
- 응답 해석:
  - `fetchedRows`: API 수신 행 수
  - `upsertAffectedRows`: DB 영향 행 수
  - `saved`: 호환 필드(`upsertAffectedRows`와 동일)
- 운영 해석:
  - `sto/sync-range`의 `skipped=true`: 해당 일자 데이터가 이미 DB에 있음
  - `idx/sync-range`의 `skipped=true` + `error=403...`: 업스트림 권한/정책 이슈
