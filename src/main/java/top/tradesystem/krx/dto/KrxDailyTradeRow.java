package top.tradesystem.krx.dto;

import java.util.Map;

public record KrxDailyTradeRow(
        String basDd,
        String isuCd,
        String isuNm,
        String mktNm,
        String sectTpNm,
        String tddClsprc,
        String cmpprevddPrc,
        String flucRt,
        String tddOpnprc,
        String tddHgprc,
        String tddLwprc,
        String accTrdvol,
        String accTrdval,
        String mktcap,
        String listShrs
) {

    public static KrxDailyTradeRow fromApiMap(String basDd, Map<String, Object> m) {
        if (m == null) return null;

        return new KrxDailyTradeRow(
                basDd,
                str(m.get("ISU_CD")),
                str(m.get("ISU_NM")),
                str(m.get("MKT_NM")),
                str(m.get("SECT_TP_NM")),
                str(m.get("TDD_CLSPRC")),
                str(m.get("CMPPREVDD_PRC")),
                str(m.get("FLUC_RT")),
                str(m.get("TDD_OPNPRC")),
                str(m.get("TDD_HGPRC")),
                str(m.get("TDD_LWPRC")),
                str(m.get("ACC_TRDVOL")),
                str(m.get("ACC_TRDVAL")),
                str(m.get("MKTCAP")),
                str(m.get("LIST_SHRS"))
        );
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
