package top.tradesystem.krx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        boolean dryrun
) {}
