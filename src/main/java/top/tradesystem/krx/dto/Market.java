package top.tradesystem.krx.dto;

public enum Market {
    KOSPI("STK"),
    KOSDAQ("KSQ");

    private final String krxCode;

    Market(String krxCode) {
        this.krxCode = krxCode;
    }

    public String krxCode() {
        return krxCode;
    }
}
