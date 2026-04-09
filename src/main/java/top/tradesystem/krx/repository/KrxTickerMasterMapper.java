package top.tradesystem.krx.repository;

import org.apache.ibatis.annotations.Param;
import top.tradesystem.krx.dto.KrxTickerMasterRow;

import java.util.List;

public interface KrxTickerMasterMapper {

    int upsertBatch(@Param("rows") List<KrxTickerMasterRow> rows);

    List<KrxTickerMasterRow> findByMarket(@Param("market") String market);

    KrxTickerMasterRow findByCode(@Param("code") String code);

    /**
     * 거래량/거래대금 상위 종목 코드 조회 (전략용)
     */
    List<String> findTopLiquidSymbols(@Param("limit") int limit);
}
