# KRX 명령어 가이드

> **Python 실행**: `python`이 PATH에 없으면 `py` 사용 (Windows Python Launcher)
> **날짜 형식**: `yyyyMMdd` (예: `20260311`)
> **서버 주소**: `http://localhost:7777` (Spring Boot `.\gradlew bootRun`)

---

## 1. 전체 파이프라인 (KRX 적재 → 전략 추천 → Slack 전송)

Spring Boot 앱 실행 상태에서 수행. 데이터 수집부터 Slack 알림까지 한 번에 처리.

```powershell
cd C:\trade-system\python

# 어제 날짜 기준 (기본값), 전체 전략
c

# 특정 날짜 지정
py -m src.run_full_pipeline --asOfDate 20260311

# 특정 전략만
py -m src.run_full_pipeline --strategies turtle,vqm,csmom

# Slack 미전송 (dry run)
py -m src.run_full_pipeline --dryRun

# KRX sync 스킵 (이미 적재된 경우)
py -m src.run_full_pipeline --skipSync

# 품질 검증 스킵
py -m src.run_full_pipeline --skipQuality

# 실패 시 Slack 알림
py -m src.run_full_pipeline --notifyOnFailure
```

## 2. Daily Batch (PowerShell 파이프라인)

```powershell
cd C:\trade-system\python

# 당일 실행
.\run_daily_pipeline.ps1 -AsOfDate (Get-Date -Format 'yyyyMMdd')

# 특정 날짜
.\run_daily_pipeline.ps1 -AsOfDate 20260311

# 재시도/실패 알림
.\run_daily_pipeline.ps1 -AsOfDate 20260311 -RetryCount 3 -RetryDelaySec 10 -NotifyOnFailure

# 옵션 (단계 스킵)
.\run_daily_pipeline.ps1 -AsOfDate 20260311 -SkipKrxSync
.\run_daily_pipeline.ps1 -AsOfDate 20260311 -SkipValuation
.\run_daily_pipeline.ps1 -AsOfDate 20260311 -SkipNotify
```

## 3. 기간별 DB 저장 (개별 API 호출)

`delayMs`는 일자별 호출 지연(ms). 과호출 방지용으로 `300~500` 권장.

```powershell
# 티커 마스터 (기간)
irm -Method Post "http://localhost:7777/internal/krx/tickers/master/sync-range?from=20260225&to=20260311&market=ALL&delayMs=300"

# 종목 일일 거래 (기간)
irm -Method Post "http://localhost:7777/svc/apis/sto/sync-range?from=20260225&to=20260311&market=ALL&delayMs=300"

# 지수 일일 시세 (기간)
irm -Method Post "http://localhost:7777/svc/apis/idx/sync-range?from=20260225&to=20260311&market=ALL&delayMs=300"
```

## 4. 단일 일자 DB 저장

```powershell
# 티커 마스터
irm -Method Post "http://localhost:7777/internal/krx/tickers/master/sync?basDd=20260311&market=ALL"

# 종목 일일 거래
irm -Method Post "http://localhost:7777/svc/apis/sto/sync-range?from=20260311&to=20260311&market=ALL&delayMs=300"

# 지수 일일 시세
irm -Method Post "http://localhost:7777/svc/apis/idx/sync-range?from=20260311&to=20260311&market=ALL&delayMs=300"
```

## 5. 조회 전용 (DB 저장 안 함)

```powershell
# 지수 조회
irm -Method Post "http://localhost:7777/svc/apis/idx/kospi_dd_trd" -ContentType "application/json" -Body '{"basDd":"20260311"}'
irm -Method Post "http://localhost:7777/svc/apis/idx/kosdaq_dd_trd" -ContentType "application/json" -Body '{"basDd":"20260311"}'

# 종목 거래 조회
irm -Method Post "http://localhost:7777/svc/apis/sto/stk_bydd_trd" -ContentType "application/json" -Body '{"basDd":"20260311"}'
irm -Method Post "http://localhost:7777/svc/apis/sto/ksq_bydd_trd" -ContentType "application/json" -Body '{"basDd":"20260311"}'

# 종목 기본정보 조회
irm -Method Post "http://localhost:7777/svc/apis/sto/stk_isu_base_info" -ContentType "application/json" -Body '{"basDd":"20260311"}'
irm -Method Post "http://localhost:7777/svc/apis/sto/ksq_isu_base_info" -ContentType "application/json" -Body '{"basDd":"20260311"}'
irm -Method Post "http://localhost:7777/svc/apis/sto/isu_base_info/range?from=20260102&to=20260311&market=ALL&delayMs=300"
```

## 6. DB 건수 확인

```sql
USE quant;
SELECT COUNT(*) AS cnt FROM krx_ticker_master;
SELECT COUNT(*) AS cnt FROM krx_daily_trade WHERE bas_dd BETWEEN '20250102' AND '20251231';
SELECT COUNT(*) AS cnt FROM krx_index_daily_price WHERE bas_dd BETWEEN '20250102' AND '20251231';
```

## 7. Spring Boot 앱 실행

```powershell
cd C:\trade-system
.\gradlew compileJava -q
.\gradlew bootRun
```

## 8. 참고사항

| 항목 | 값 |
|---|---|
| `market` 허용값 | `KOSPI`, `KOSDAQ`, `ALL` |
| `delayMs` 기본값 | `0` |
| 전략 목록 | `turtle`, `vqm`, `lowvol`, `fvg`, `trend`, `flow`, `csmom`, `dualmom`, `tsmom`, `value`, `quality`, `riskparity`, `carry`, `pairs`, `reversal`, `volmanaged` |

**응답 필드 해석**:
- `fetchedRows`: API 수신 행 수
- `upsertAffectedRows`: DB 영향 행 수
- `saved`: 호환 필드 (`upsertAffectedRows`와 동일)

**운영 해석**:
- `sto/sync-range`의 `skipped=true`: 해당 일자 데이터가 이미 DB에 있음
- `idx/sync-range`의 `skipped=true` + `error=403...`: 업스트림 권한/정책 이슈
