use quant;

SHOW CREATE TABLE krx_api_raw_log;

SHOW CREATE TABLE krx_kospi_daily_trading;

ALTER TABLE krx_api_raw_log
  ADD COLUMN api_endpoint VARCHAR(256) NULL AFTER api_name;
  
  ALTER TABLE krx_api_raw_log
  ADD COLUMN request_params JSON NULL AFTER bas_dd;
  
  
  ALTER TABLE krx_api_raw_log ADD COLUMN api_endpoint VARCHAR(256) NULL AFTER api_name;
  
  
  ALTER TABLE krx_kospi_isu_base_info
  MODIFY parval VARCHAR(32) NULL;
  
  select * from krx_ticker_master;
  select count(*) from krx_ticker_master;

  select * from krx_daily_trade where bas_dd between '20260102' and '20260203';
  select count(*) from krx_daily_trade;
  select bas_dd, mkt_nm, count(*) from krx_daily_trade where bas_dd between '20260102' and '20260203' group by bas_dd, mkt_nm;
  select count(*) from krx_daily_trade  where bas_dd between '20260102' and '20260203' ;
  -- delete from krx_daily_trade  where bas_dd between '20260102' and '20260203' ;

  select * from krx_daily_price  where bas_dd between '20260102' and '20260203';
  select count(*) from krx_daily_price;
  select count(*) from krx_daily_price  where bas_dd between '20110102' and '20111231'  ;

  select * from krx_ticker_master where name_kr like '%삼성%';
  
  delete from krx_ticker_master;
  delete from krx_daily_trade;
  
  SHOW COLUMNS FROM krx_daily_trade;
  
  
  
  select * from krx_batch_checkpoint;
  select * from krx_batch_state;
  
  
  
SELECT mkt_nm, COUNT(*) 
FROM krx_daily_trade
WHERE bas_dd BETWEEN '19560303' AND '20260123'
GROUP BY mkt_nm;

SELECT * FROM dart_corp_code;
SELECT count(*) FROM dart_disclosure_master;
SELECT count(*) FROM dart_financials_snapshot where bsns_year = '2025';
SELECT count(*) FROM krx_daily_valuation where bas_dd = (select max(bas_dd) from krx_daily_valuation);
SELECT count(*) FROM krx_daily_valuation where bas_dd = '20260202';