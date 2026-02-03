package top.tradesystem.krx.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.tradesystem.krx.dto.KrxDailyPriceRow;

import java.util.List;

@Mapper
public interface KrxDailyPriceMapper {

    int upsertBatch(@Param("rows") List<KrxDailyPriceRow> rows);

    List<KrxDailyPriceRow> findByBasDdAndMarket(
            @Param("basDd") String basDd,
            @Param("market") String market
    );

    KrxDailyPriceRow findByBasDdAndCode(
            @Param("basDd") String basDd,
            @Param("code") String code
    );
}
