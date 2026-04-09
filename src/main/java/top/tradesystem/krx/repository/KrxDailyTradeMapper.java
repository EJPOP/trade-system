package top.tradesystem.krx.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.tradesystem.krx.dto.KrxDailyTradeRow;

import java.util.List;

@Mapper
public interface KrxDailyTradeMapper {

    int upsertBatch(@Param("rows") List<KrxDailyTradeRow> rows);
    int countByBasDdAndMarket(@Param("basDd") String basDd, @Param("market") String market);

    List<KrxDailyTradeRow> findByBasDdAndMarket(
            @Param("basDd") String basDd,
            @Param("market") String market
    );

    KrxDailyTradeRow findByBasDdAndCode(
            @Param("basDd") String basDd,
            @Param("code") String code
    );

    /**
     * 특정 종목의 과거 시세를 날짜순으로 조회 (백테스트용)
     */
    List<KrxDailyTradeRow> findBySymbolOrderByBasDd(@Param("symbol") String symbol);
}
