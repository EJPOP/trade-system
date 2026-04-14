package top.tradesystem.krx.trading.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.tradesystem.krx.dto.KrxDailyTradeRow;

import java.util.List;
import java.util.Map;

@Mapper
public interface TradingMapper {
    /**
     * 거래대금 상위 종목 조회 (기존 krx_daily_trade 테이블 활용)
     */
    List<String> findTopLiquidSymbols(@Param("limit") int limit);

    /**
     * 특정 기간 동안의 RS(상대적 강도) 산출을 위한 수익률 순위 조회
     */
    List<Map<String, Object>> findRelativeStrengthRank(@Param("days") int days);

    /**
     * 최근 N거래일간 기관/외인 순매수 합계 조회
     */
    List<Map<String, Object>> findInstitutionalAccumulation(@Param("days") int days);

    /**
     * 특정 종목의 과거 시세 조회 (백테스트용)
     */
    List<KrxDailyTradeRow> findBySymbolOrderByBasDd(@Param("symbol") String symbol);
}
