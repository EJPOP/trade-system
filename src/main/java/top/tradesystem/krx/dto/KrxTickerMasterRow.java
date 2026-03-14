package top.tradesystem.krx.dto;

import java.time.LocalDate;

public record KrxTickerMasterRow(
        String code,
        String isin,
        String nameKr,
        String nameKrAbbr,
        String nameEn,
        String market,
        String secGroup,
        String kindStockCert,
        LocalDate listDate,
        String parValue,
        String listShares
) {}
