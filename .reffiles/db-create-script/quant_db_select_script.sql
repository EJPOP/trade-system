USE quant;

-- ============================================================
-- 공통 파라미터
-- ------------------------------------------------------------
-- 모든 bas_dd 범위 조회는 아래 변수(@from, @to)를 사용합니다.
-- 실행 전에 원하는 날짜로 수정하세요. (형식: YYYYMMDD)
-- ============================================================
SET @from = '20260102';
SET @to   = '20260203';

-- ============================================================
-- 1) krx_ticker_master
-- ------------------------------------------------------------
-- 성격:
--   - 종목 마스터 테이블
--   - bas_dd 컬럼이 없음 (종목 속성 스냅샷)
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM krx_ticker_master;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM krx_ticker_master;

-- [from-to 조회]
-- bas_dd 컬럼이 없으므로 날짜 범위 조회 불가
-- 필요 시 list_date 기준으로 대체 조회
SELECT *
FROM krx_ticker_master
WHERE list_date BETWEEN STR_TO_DATE(@from, '%Y%m%d') AND STR_TO_DATE(@to, '%Y%m%d');

-- [bas_dd 기준 조회]
-- bas_dd 컬럼이 없어 해당 없음

-- [추가 검색 예시]
SELECT * FROM krx_ticker_master WHERE name_kr LIKE '%삼성%';

-- ============================================================
-- 2) krx_daily_trade
-- ------------------------------------------------------------
-- 성격:
--   - KRX 일별 종목 거래/시세 원본
--   - bas_dd 컬럼 있음
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM krx_daily_trade;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM krx_daily_trade;

-- [from-to 범위 데이터]
SELECT *
FROM krx_daily_trade
WHERE bas_dd BETWEEN @from AND @to
ORDER BY bas_dd, isu_cd;

-- [from-to 범위 건수]
SELECT COUNT(*) AS cnt
FROM krx_daily_trade
WHERE bas_dd BETWEEN @from AND @to;

-- [bas_dd 기준 일자별 건수]
SELECT bas_dd, COUNT(*) AS cnt
FROM krx_daily_trade
GROUP BY bas_dd
ORDER BY bas_dd;

-- [bas_dd + 시장 구분 건수]
SELECT bas_dd, mkt_nm, COUNT(*) AS cnt
FROM krx_daily_trade
WHERE bas_dd BETWEEN @from AND @to
GROUP BY bas_dd, mkt_nm
ORDER BY bas_dd, mkt_nm;

-- ============================================================
-- 3) krx_index_daily_price
-- ------------------------------------------------------------
-- 성격:
--   - KRX 지수 일별 시세
--   - bas_dd 컬럼 있음
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM krx_index_daily_price;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM krx_index_daily_price;

-- [from-to 범위 데이터]
SELECT *
FROM krx_index_daily_price
WHERE bas_dd BETWEEN @from AND @to
ORDER BY bas_dd, market, idx_nm;

-- [from-to 범위 건수]
SELECT COUNT(*) AS cnt
FROM krx_index_daily_price
WHERE bas_dd BETWEEN @from AND @to;

-- [bas_dd 기준 일자별 건수]
SELECT bas_dd, COUNT(*) AS cnt
FROM krx_index_daily_price
GROUP BY bas_dd
ORDER BY bas_dd;

-- ============================================================
-- 4) krx_daily_valuation
-- ------------------------------------------------------------
-- 성격:
--   - 종목 일별 밸류에이션 계산 결과
--   - bas_dd 컬럼 있음
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM krx_daily_valuation;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM krx_daily_valuation;

-- [from-to 범위 데이터]
SELECT *
FROM krx_daily_valuation
WHERE bas_dd BETWEEN @from AND @to
ORDER BY bas_dd, isu_cd;

-- [from-to 범위 건수]
SELECT COUNT(*) AS cnt
FROM krx_daily_valuation
WHERE bas_dd BETWEEN @from AND @to;

-- [bas_dd 기준 일자별 건수]
SELECT bas_dd, COUNT(*) AS cnt
FROM krx_daily_valuation
GROUP BY bas_dd
ORDER BY bas_dd;

-- [최신 일자 건수]
SELECT COUNT(*) AS cnt
FROM krx_daily_valuation
WHERE bas_dd = (SELECT MAX(bas_dd) FROM krx_daily_valuation);

-- ============================================================
-- 5) dart_financials_snapshot
-- ------------------------------------------------------------
-- 성격:
--   - DART 재무 스냅샷
--   - bas_dd 컬럼이 없음 (연도/보고서 코드 중심)
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM dart_financials_snapshot;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM dart_financials_snapshot;

-- [from-to 조회]
-- bas_dd 컬럼이 없으므로 날짜 범위 조회 불가
-- 필요 시 bsns_year 조건으로 대체
SELECT *
FROM dart_financials_snapshot
WHERE bsns_year BETWEEN LEFT(@from, 4) AND LEFT(@to, 4);

-- [bas_dd 기준 조회]
-- bas_dd 컬럼이 없어 해당 없음

-- [연도별 건수]
SELECT bsns_year, COUNT(*) AS cnt
FROM dart_financials_snapshot
GROUP BY bsns_year
ORDER BY bsns_year;

-- ============================================================
-- 6) dart_disclosure_master
-- ------------------------------------------------------------
-- 성격:
--   - DART 공시 마스터
--   - bas_dd 컬럼이 없음
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM dart_disclosure_master;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM dart_disclosure_master;

-- [from-to 조회]
-- bas_dd 컬럼이 없으므로 날짜 범위 조회 불가
-- 테이블 컬럼에 따라 rcept_dt가 있으면 아래처럼 사용 가능
-- SELECT * FROM dart_disclosure_master WHERE rcept_dt BETWEEN @from AND @to;

-- [bas_dd 기준 조회]
-- bas_dd 컬럼이 없어 해당 없음

-- ============================================================
-- 7) dart_corp_code
-- ------------------------------------------------------------
-- 성격:
--   - DART 기업코드 매핑
--   - bas_dd 컬럼이 없음
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM dart_corp_code;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM dart_corp_code;

-- [from-to 조회]
-- bas_dd 컬럼이 없어 해당 없음

-- [bas_dd 기준 조회]
-- bas_dd 컬럼이 없어 해당 없음

-- ============================================================
-- 8) krx_batch_checkpoint
-- ------------------------------------------------------------
-- 성격:
--   - 배치 실행 체크포인트
--   - bas_dd 컬럼 여부는 스키마 구현에 따라 다를 수 있음
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM krx_batch_checkpoint;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM krx_batch_checkpoint;

-- [from-to / bas_dd 조회]
-- 아래 쿼리는 bas_dd 컬럼이 실제로 있을 때만 사용
-- SELECT * FROM krx_batch_checkpoint WHERE bas_dd BETWEEN @from AND @to;
-- SELECT bas_dd, COUNT(*) AS cnt FROM krx_batch_checkpoint GROUP BY bas_dd ORDER BY bas_dd;

-- ============================================================
-- 9) krx_batch_state
-- ------------------------------------------------------------
-- 성격:
--   - 배치 상태 테이블
--   - bas_dd 컬럼 여부는 스키마 구현에 따라 다를 수 있음
-- ============================================================

-- [전체 데이터 조회]
SELECT * FROM krx_batch_state;

-- [전체 건수]
SELECT COUNT(*) AS cnt FROM krx_batch_state;

-- [from-to / bas_dd 조회]
-- 아래 쿼리는 bas_dd 컬럼이 실제로 있을 때만 사용
-- SELECT * FROM krx_batch_state WHERE bas_dd BETWEEN @from AND @to;
-- SELECT bas_dd, COUNT(*) AS cnt FROM krx_batch_state GROUP BY bas_dd ORDER BY bas_dd;

-- ============================================================
-- 안전 장치: 삭제 쿼리는 기본 주석 처리
-- ------------------------------------------------------------
-- 실제 운영 DB에서 실행 전에 반드시 WHERE 조건을 점검하세요.
-- ============================================================
-- DELETE FROM krx_daily_trade WHERE bas_dd BETWEEN @from AND @to;
-- DELETE FROM krx_index_daily_price WHERE bas_dd BETWEEN @from AND @to;
-- DELETE FROM krx_daily_valuation WHERE bas_dd BETWEEN @from AND @to;
