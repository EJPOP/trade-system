package top.tradesystem.krx.trading.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import top.tradesystem.krx.config.KisProperties;
import top.tradesystem.krx.config.TradingProperties;

@Configuration
@EnableConfigurationProperties({KisProperties.class, TradingProperties.class})
public class TradingConfig {
    // 자동매매를 위한 별도 설정 클래스
}
