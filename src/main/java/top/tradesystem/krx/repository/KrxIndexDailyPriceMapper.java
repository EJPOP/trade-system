package top.tradesystem.krx.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.tradesystem.krx.dto.KrxIndexDailyPriceRow;

import java.util.List;

@Mapper
public interface KrxIndexDailyPriceMapper {

    int upsertBatch(@Param("rows") List<KrxIndexDailyPriceRow> rows);

    List<KrxIndexDailyPriceRow> findByBasDdAndMarket(
            @Param("basDd") String basDd,
            @Param("market") String market
    );
}
