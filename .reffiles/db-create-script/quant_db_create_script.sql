use quant;

-- ticker master
CREATE TABLE IF NOT EXISTS krx_ticker_master (
  code            VARCHAR(16)  NOT NULL PRIMARY KEY,
  isin            VARCHAR(32)  NULL,
  name_kr         VARCHAR(200) NULL,
  name_kr_abbr    VARCHAR(200) NULL,
  name_en         VARCHAR(200) NULL,
  market          VARCHAR(32)  NULL,
  sec_group       VARCHAR(64)  NULL,
  kind_stock_cert VARCHAR(64)  NULL,
  list_date       DATE         NULL,
  par_value       VARCHAR(32)  NULL,
  list_shares     VARCHAR(32)  NULL,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_krx_ticker_market ON krx_ticker_master (market);
CREATE INDEX idx_krx_ticker_isin   ON krx_ticker_master (isin);

-- stock daily trade
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

-- index daily price (KRX idx/*)
CREATE TABLE IF NOT EXISTS krx_index_daily_price (
  bas_dd         VARCHAR(8)    NOT NULL,
  market         VARCHAR(16)   NOT NULL,
  idx_clss       VARCHAR(50)   NULL,
  idx_nm         VARCHAR(120)  NOT NULL,
  clsprc_idx     DECIMAL(18,4) NULL,
  cmpprevdd_idx  DECIMAL(18,4) NULL,
  fluc_rt        DECIMAL(12,6) NULL,
  opnprc_idx     DECIMAL(18,4) NULL,
  hgprc_idx      DECIMAL(18,4) NULL,
  lwprc_idx      DECIMAL(18,4) NULL,
  acc_trdvol     DECIMAL(30,4) NULL,
  acc_trdval     DECIMAL(30,0) NULL,
  mktcap         DECIMAL(30,0) NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (bas_dd, market, idx_nm),
  KEY idx_krx_index_daily_price_market (market, bas_dd),
  KEY idx_krx_index_daily_price_name (idx_nm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
