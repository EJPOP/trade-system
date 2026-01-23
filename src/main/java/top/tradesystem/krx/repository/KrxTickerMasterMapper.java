package top.tradesystem.krx.repository;

import org.apache.ibatis.annotations.Param;
import top.tradesystem.krx.dto.KrxTickerMasterRow;

import java.util.List;

public interface KrxTickerMasterMapper {

    int upsertBatch(@Param("rows") List<KrxTickerMasterRow> rows);

    List<KrxTickerMasterRow> findByMarket(@Param("market") String market);

    // ✅ 종목코드 단건 조회
    KrxTickerMasterRow findByCode(@Param("code") String code);
}
