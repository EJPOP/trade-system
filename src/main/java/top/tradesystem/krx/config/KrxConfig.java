package top.tradesystem.krx.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({KrxProperties.class, KisProperties.class, TradingProperties.class})
public class KrxConfig {
}
