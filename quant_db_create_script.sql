use quant;

-- 종목정보
CREATE TABLE IF NOT EXISTS krx_ticker_master (
  code            VARCHAR(16)  NOT NULL PRIMARY KEY,  -- ISU_SRT_CD
  isin            VARCHAR(32)  NULL,                   -- ISU_CD
  name_kr         VARCHAR(200) NULL,                   -- ISU_NM
  name_kr_abbr    VARCHAR(200) NULL,                   -- ISU_ABBRV
  name_en         VARCHAR(200) NULL,                   -- ISU_ENG_NM
  market          VARCHAR(32)  NULL,                   -- MKT_TP_NM
  sec_group       VARCHAR(64)  NULL,                   -- SECUGRP_NM
  kind_stock_cert VARCHAR(64)  NULL,                   -- KIND_STKCERT_TP_NM
  list_date       DATE         NULL,                   -- LIST_DD
  par_value       VARCHAR(32)  NULL,                   -- PARVAL
  list_shares     VARCHAR(32)  NULL,                   -- LIST_SHRS
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_krx_ticker_market ON krx_ticker_master (market);
CREATE INDEX idx_krx_ticker_isin   ON krx_ticker_master (isin);


-- 일별 매매정보
CREATE TABLE IF NOT EXISTS krx_daily_trade (
    bas_dd         VARCHAR(8)  NOT NULL,
    isu_cd         VARCHAR(16) NOT NULL,
    isu_nm         VARCHAR(200),
    mkt_nm         VARCHAR(20),
    sect_tp_nm     VARCHAR(50),

    tdd_clsprc     VARCHAR(50),
    cmpprevdd_prc  VARCHAR(50),
    fluc_rt        VARCHAR(50),
    tdd_opnprc     VARCHAR(50),
    tdd_hgprc      VARCHAR(50),
    tdd_lwprc      VARCHAR(50),

    acc_trdvol     VARCHAR(50),
    acc_trdval     VARCHAR(50),
    mktcap         VARCHAR(50),
    list_shrs      VARCHAR(50),

    PRIMARY KEY (bas_dd, isu_cd),
    INDEX idx_kdt_market (mkt_nm),
    INDEX idx_kdt_code (isu_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 일별 시세정보
CREATE TABLE IF NOT EXISTS krx_daily_price (
  bas_dd        VARCHAR(8)   NOT NULL,         -- yyyyMMdd
  market        VARCHAR(16)  NOT NULL,         -- KOSPI / KOSDAQ
  isu_cd        VARCHAR(12)  NOT NULL,         -- 종목코드
  isu_nm        VARCHAR(100) NULL,             -- 종목명
  sect_tp_nm    VARCHAR(60)  NULL,             -- 섹터(있으면)

  tdd_clsprc    DECIMAL(18,2) NULL,            -- 종가
  cmpprevdd_prc DECIMAL(18,2) NULL,            -- 대비
  fluc_rt       DECIMAL(9,4)  NULL,            -- 등락률(%)

  tdd_opnprc    DECIMAL(18,2) NULL,            -- 시가
  tdd_hgprc     DECIMAL(18,2) NULL,            -- 고가
  tdd_lwprc     DECIMAL(18,2) NULL,            -- 저가

  acc_trdvol    BIGINT NULL,                   -- 거래량
  acc_trdval    DECIMAL(30,0) NULL,            -- 거래대금
  mktcap        DECIMAL(30,0) NULL,            -- 시총
  list_shrs     BIGINT NULL,                   -- 상장주식수

  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (bas_dd, isu_cd),
  KEY idx_krx_daily_price_market (market, bas_dd)
);


