package top.tradesystem.krx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.tradesystem.krx.client.KisOpenApiClient;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradingStrategyService {

    private static final Logger log = LoggerFactory.getLogger(TradingStrategyService.class);
    private final KisOpenApiClient apiClient;
    private final top.tradesystem.krx.repository.KrxTickerMasterMapper tickerMapper;

    // 종목별 마지막 가격 및 거래 정보 저장 (스캘핑/전략용)
    private final ConcurrentHashMap<String, Long> lastPriceMap = new ConcurrentHashMap<>();

    public TradingStrategyService(KisOpenApiClient apiClient, top.tradesystem.krx.repository.KrxTickerMasterMapper tickerMapper) {
        this.apiClient = apiClient;
        this.tickerMapper = tickerMapper;
    }

    /**
     * 감시 대상 종목 선정 (거래대금 상위)
     */
    public java.util.List<String> getTargetSymbols(int limit) {
        return tickerMapper.findTopLiquidSymbols(limit);
    }

    /**
     * 실시간 체결 정보를 처리하고 전략에 따라 주문 실행
     * @param price 현재가
     * @param volume 거래량
     * @param strength 체결강도 (%)
     */
    public void processRealtimeTick(String symbol, long price, long volume, double strength) {
        Long prevPrice = lastPriceMap.get(symbol);
        lastPriceMap.put(symbol, price);

        LocalTime now = LocalTime.now();
        
        // 08:00 ~ 09:00: NXT/프리마켓 공격적 매매
        if (now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(9, 0))) {
            // 체결강도가 100% 이상이고 상승 모멘텀일 때만 공격적 매수
            if (prevPrice != null && price > prevPrice * 1.01 && strength > 100.0) {
                log.info("[NXT-AGGRESSIVE] Symbol: {}, Price: {}, Strength: {}%", symbol, price, strength);
                executeOrder(true, symbol, 10, (long)(price * 1.01), "00"); 
            }
            return;
        }

        if (prevPrice == null) return;

        // 정규장 스캘핑: 낙폭 과대 + 체결강도 반등 확인
        double changeRate = ((double) price - prevPrice) / prevPrice;
        if (changeRate < -0.01 && strength > 105.0) {
            log.info("[SCALPING-ENTRY] Symbol: {}, Price: {}, Strength: {}", symbol, price, strength);
            executeOrder(true, symbol, 5, 0, "01"); // 시장가 매수
        }
    }

    private void executeOrder(boolean isBuy, String symbol, int qty, long price, String ordDvsn) {
        apiClient.postOrder(isBuy, symbol, qty, price, ordDvsn)
                .subscribe(
                    res -> log.info("Order Placed: {}", res),
                    err -> log.error("Order Failed: {}", err.getMessage())
                );
    }

    private void executeOrder(boolean isBuy, String symbol, int qty, long price) {
        executeOrder(isBuy, symbol, qty, price, price == 0 ? "01" : "00");
    }
}
