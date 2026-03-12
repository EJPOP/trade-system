use quant;

-- ============================================================
-- US Stock Market Tables
-- ============================================================

-- US ticker master (S&P 500 + major indices)
CREATE TABLE IF NOT EXISTS us_ticker_master (
  symbol          VARCHAR(20)  NOT NULL PRIMARY KEY,
  name            VARCHAR(300) NULL,
  sector          VARCHAR(100) NULL,
  industry        VARCHAR(200) NULL,
  exchange        VARCHAR(20)  NULL,
  market_cap      BIGINT       NULL,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_us_ticker_sector   ON us_ticker_master (sector);
CREATE INDEX idx_us_ticker_exchange ON us_ticker_master (exchange);

-- US daily trade (OHLCV)
CREATE TABLE IF NOT EXISTS us_daily_trade (
  bas_dd          VARCHAR(8)     NOT NULL,
  symbol          VARCHAR(20)    NOT NULL,
  open_prc        DECIMAL(18,4)  NULL,
  high_prc        DECIMAL(18,4)  NULL,
  low_prc         DECIMAL(18,4)  NULL,
  close_prc       DECIMAL(18,4)  NULL,
  adj_close       DECIMAL(18,4)  NULL,
  volume          BIGINT         NULL,
  PRIMARY KEY (bas_dd, symbol),
  INDEX idx_udt_symbol (symbol),
  INDEX idx_udt_date   (bas_dd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- US sector daily performance (sector ETF tracking)
CREATE TABLE IF NOT EXISTS us_sector_daily_performance (
  bas_dd          VARCHAR(8)     NOT NULL,
  sector          VARCHAR(100)   NOT NULL,
  etf_symbol      VARCHAR(10)    NULL,
  close_prc       DECIMAL(18,4)  NULL,
  prev_close      DECIMAL(18,4)  NULL,
  change_pct      DECIMAL(10,4)  NULL,
  volume          BIGINT         NULL,
  PRIMARY KEY (bas_dd, sector),
  INDEX idx_usdp_date (bas_dd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
