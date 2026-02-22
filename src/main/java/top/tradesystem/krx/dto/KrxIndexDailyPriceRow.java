package top.tradesystem.krx.dto;

import java.math.BigDecimal;
import java.util.Map;

public record KrxIndexDailyPriceRow(
        String basDd,
        String market,
        String idxClss,
        String idxNm,
        BigDecimal clsprcIdx,
        BigDecimal cmpprevddIdx,
        BigDecimal flucRt,
        BigDecimal opnprcIdx,
        BigDecimal hgprcIdx,
        BigDecimal lwprcIdx,
        BigDecimal accTrdvol,
        BigDecimal accTrdval,
        BigDecimal mktcap
) {
    public static KrxIndexDailyPriceRow fromApiMap(String basDd, String market, Map<String, ?> m) {
        if (m == null) return null;

        return new KrxIndexDailyPriceRow(
                basDd,
                market,
                s(m.get("IDX_CLSS")),
                s(m.get("IDX_NM")),
                bd(m.get("CLSPRC_IDX")),
                bd(m.get("CMPPREVDD_IDX")),
                bd(m.get("FLUC_RT")),
                bd(m.get("OPNPRC_IDX")),
                bd(m.get("HGPRC_IDX")),
                bd(m.get("LWPRC_IDX")),
                bd(m.get("ACC_TRDVOL")),
                bd(m.get("ACC_TRDVAL")),
                bd(m.get("MKTCAP"))
        );
    }

    private static String s(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static BigDecimal bd(Object o) {
        if (o == null) return null;
        String v = String.valueOf(o).trim();
        if (v.isEmpty() || "-".equals(v)) return null;
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
