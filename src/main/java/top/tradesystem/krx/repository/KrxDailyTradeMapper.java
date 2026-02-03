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
}
