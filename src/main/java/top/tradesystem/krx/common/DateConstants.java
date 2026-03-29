package top.tradesystem.krx.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * Shared date-format constants used across service classes.
 */
public final class DateConstants {

    private DateConstants() {}

    /** yyyyMMdd STRICT formatter — 20240230 같은 잘못된 날짜를 거부. */
    public static final DateTimeFormatter BAS_DD_FMT =
            DateTimeFormatter.ofPattern("uuuuMMdd")
                    .withResolverStyle(ResolverStyle.STRICT);

    /** basDd 문자열 검증 — 형식 + 실제 달력 날짜 유효성. */
    public static void validateBasDd(String basDd) {
        if (basDd == null || !basDd.matches("\\d{8}")) {
            throw new IllegalArgumentException("basDd must be YYYYMMDD format: " + basDd);
        }
        try {
            LocalDate.parse(basDd, BAS_DD_FMT);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid calendar date: " + basDd, e);
        }
    }
}
