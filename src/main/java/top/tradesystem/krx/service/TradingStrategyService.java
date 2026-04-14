package top.tradesystem.krx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.tradesystem.krx.client.KisOpenApiClient;
import top.tradesystem.krx.config.TradingProperties;
import top.tradesystem.krx.trading.repository.TradingMapper;
import top.tradesystem.krx.trading.service.LiquidityHuntingService; // ✅ 추가

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradingStrategyService {

    private static final Logger log = LoggerFactory.getLogger(TradingStrategyService.class);
    private final KisOpenApiClient apiClient;
    private final TradingMapper tradingMapper;
    private final TradingProperties tradingProps;
    private final LiquidityHuntingService liquidityHuntingService;

    private final ConcurrentHashMap<String, Long> lastPriceMap = new ConcurrentHashMap<>();

    public TradingStrategyService(
            KisOpenApiClient apiClient, 
            TradingMapper tradingMapper, 
            TradingProperties tradingProps,
            LiquidityHuntingService liquidityHuntingService) {
        this.apiClient = apiClient;
        this.tradingMapper = tradingMapper;
        this.tradingProps = tradingProps;
        this.liquidityHuntingService = liquidityHuntingService;
    }

    public List<String> getTargetSymbols(int limit) {
        return tradingMapper.findTopLiquidSymbols(limit);
    }

    public void processRealtimeOFI(String symbol, long totalBidQty, long totalAskQty) {
        double ofi = liquidityHuntingService.calculateOFI(symbol, totalBidQty, totalAskQty);
        if (ofi > 10000) {
            log.info("[OFI-SIGNAL] BUY {}", symbol);
            executeOrder(true, symbol, 5, 0, "01");
        }
    }

    public void processRealtimeTick(String symbol, long price, long volume, double strength) {
        Long prevPrice = lastPriceMap.get(symbol);
        lastPriceMap.put(symbol, price);
        LocalTime now = LocalTime.now();
        
        if (now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(9, 0))) {
            if (prevPrice != null && price > prevPrice * 1.01 && strength > 100.0) {
                executeOrder(true, symbol, 10, (long)(price * 1.01), "00"); 
            }
            return;
        }

        if (prevPrice == null) return;
        double changeRate = ((double) price - prevPrice) / prevPrice;
        if (changeRate < -0.01 && strength > 105.0) {
            executeOrder(true, symbol, 5, 0, "01"); 
        }
    }

    private void executeOrder(boolean isBuy, String symbol, int qty, long price, String ordDvsn) {
        if (tradingProps.dryrun()) {
            log.info("[DRYRUN] {} {} Qty: {}, Price: {}", isBuy ? "BUY" : "SELL", symbol, qty, price);
            return;
        }
        apiClient.postOrder(isBuy, symbol, qty, price, ordDvsn)
                .subscribe(res -> log.info("Order Result: {}", res));
    }
}
