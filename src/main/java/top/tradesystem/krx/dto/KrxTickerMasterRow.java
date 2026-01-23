package top.tradesystem.krx.dto;

import java.time.LocalDate;

public class KrxTickerMasterRow {
    private String code;
    private String isin;
    private String nameKr;
    private String nameKrAbbr;
    private String nameEn;
    private String market;
    private String secGroup;
    private String kindStockCert;
    private LocalDate listDate;
    private String parValue;
    private String listShares;

    public KrxTickerMasterRow() {}

    public KrxTickerMasterRow(String code, String isin, String nameKr, String nameKrAbbr, String nameEn,
                              String market, String secGroup, String kindStockCert, LocalDate listDate,
                              String parValue, String listShares) {
        this.code = code;
        this.isin = isin;
        this.nameKr = nameKr;
        this.nameKrAbbr = nameKrAbbr;
        this.nameEn = nameEn;
        this.market = market;
        this.secGroup = secGroup;
        this.kindStockCert = kindStockCert;
        this.listDate = listDate;
        this.parValue = parValue;
        this.listShares = listShares;
    }

    public String getCode() { return code; }
    public String getIsin() { return isin; }
    public String getNameKr() { return nameKr; }
    public String getNameKrAbbr() { return nameKrAbbr; }
    public String getNameEn() { return nameEn; }
    public String getMarket() { return market; }
    public String getSecGroup() { return secGroup; }
    public String getKindStockCert() { return kindStockCert; }
    public LocalDate getListDate() { return listDate; }
    public String getParValue() { return parValue; }
    public String getListShares() { return listShares; }
}
