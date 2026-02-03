package top.tradesystem.krx.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record KrxDailyPriceRow(
        String basDd,
        String market,
        String isuCd,
        String isuNm,
        String sectTpNm,
        BigDecimal tddClsprc,
        BigDecimal cmpprevddPrc,
        BigDecimal flucRt,
        BigDecimal tddOpnprc,
        BigDecimal tddHgprc,
        BigDecimal tddLwprc,
        Long accTrdvol,
        BigDecimal accTrdval,
        BigDecimal mktcap,
        Long listShrs
) {
    // KRX Map -> DTO (DB 스키마 기준)
    public static KrxDailyPriceRow fromApiMap(String basDd, String market, Map<String, ?> m) {
        if (m == null) return null;

        // 방어적으로 키 접근(대문자/언더스코어)
        String isuCd = str(m.get("ISU_CD"));
        String isuNm = str(m.get("ISU_NM"));
        String sectTpNm = str(m.get("SECT_TP_NM"));

        // 일부 응답은 시장명이 MKT_NM 으로 오기도 함. 없으면 파라미터 market 사용
        String mkt = firstNonBlank(str(m.get("MKT_NM")), market);

        return new KrxDailyPriceRow(
                basDd,
                mkt,
                isuCd,
                isuNm,
                sectTpNm,
                dec(m.get("TDD_CLSPRC")),
                dec(m.get("CMPPREVDD_PRC")),
                dec(m.get("FLUC_RT")),
                dec(m.get("TDD_OPNPRC")),
                dec(m.get("TDD_HGPRC")),
                dec(m.get("TDD_LWPRC")),
                lng(m.get("ACC_TRDVOL")),
                dec(m.get("ACC_TRDVAL")),
                dec(m.get("MKTCAP")),
                lng(m.get("LIST_SHRS"))
        );
    }

    // Map<String,String> -> Map<String,Object> 로 캐스팅이 필요할 때 사용
    public static Map<String, Object> toObjectMap(Map<String, String> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in != null) out.putAll(in);
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static java.math.BigDecimal dec(Object o) {
        String s = str(o);
        if (s == null || s.isBlank() || "-".equals(s)) return null;
        s = s.replace(",", "");
        try { return new java.math.BigDecimal(s); } catch (Exception e) { return null; }
    }

    private static Long lng(Object o) {
        String s = str(o);
        if (s == null || s.isBlank() || "-".equals(s)) return null;
        s = s.replace(",", "");
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }
}
