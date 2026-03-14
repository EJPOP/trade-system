package top.tradesystem.krx.common;

import java.time.format.DateTimeFormatter;

/**
 * Shared date-format constants used across service classes.
 */
public final class DateConstants {

    private DateConstants() {}

    /** yyyyMMdd formatter — the standard date format for KRX base dates. */
    public static final DateTimeFormatter BAS_DD_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
}
