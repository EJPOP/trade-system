package top.tradesystem.krx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "krx")
public record KrxProperties(
        String baseUrl,
        String authKey,
        int timeoutSeconds,
        String responseCharset
) {
    public KrxProperties {
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (responseCharset == null || responseCharset.isBlank()) responseCharset = "MS949"; // KRX가 종종 EUC-KR/MS949
    }
}
