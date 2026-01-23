package top.tradesystem.krx.dto;

import java.math.BigDecimal;
import java.util.Map;

public record KrxIndexDailyPriceRow(
        String basDd,
        String idxNm,
        BigDecimal clpr,
        BigDecimal vs,
        BigDecimal flucRt,
        BigDecimal opnprc,
        BigDecimal hgprc,
        BigDecimal lwprc,
        BigDecimal accTrdvol,
        BigDecimal accTrdval
) {
    public static KrxIndexDailyPriceRow fromApiMap(String basDd, Map<String, ?> m) {
        if (m == null) return null;

        // 실제 키는 API마다 조금씩 다를 수 있어(명세서/샘플 응답 기준으로 맞추면 됨)
        // 아래는 흔한 네이밍 패턴 예시
        String idxNm = s(m.get("IDX_NM"));

        return new KrxIndexDailyPriceRow(
                basDd,
                idxNm,
                bd(m.get("CLPR")),
                bd(m.get("VS")),
                bd(m.get("FLUC_RT")),
                bd(m.get("OPNPRC")),
                bd(m.get("HGPRC")),
                bd(m.get("LWPRC")),
                bd(m.get("ACC_TRDVOL")),
                bd(m.get("ACC_TRDVAL"))
        );
    }

    private static String s(Object o) { return o == null ? null : String.valueOf(o); }

    private static BigDecimal bd(Object o) {
        if (o == null) return null;
        String v = String.valueOf(o).trim();
        if (v.isEmpty() || "-".equals(v)) return null;
        try {
            // 콤마 제거
            v = v.replace(",", "");
            return new BigDecimal(v);
        } catch (Exception e) {
            return null;
        }
    }
}
