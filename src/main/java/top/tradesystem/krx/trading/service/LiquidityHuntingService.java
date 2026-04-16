package top.tradesystem.krx.trading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiquidityHuntingService {
    private static final Logger log = LoggerFactory.getLogger(LiquidityHuntingService.class);

    // 종목별 이전 호가 잔량 저장 (OFI 계산용)
    private final ConcurrentHashMap<String, QuoteState> prevQuotes = new ConcurrentHashMap<>();

    /**
     * OFI(Order Flow Imbalance) 계산 로직
     * OFI = (Bid_Qty_t - Bid_Qty_t-1) - (Ask_Qty_t - Ask_Qty_t-1)
     */
    public double calculateOFI(String symbol, long totalBidQty, long totalAskQty) {
        QuoteState prev = prevQuotes.get(symbol);
        double ofi = 0;

        if (prev != null) {
            long bidChange = totalBidQty - prev.bidQty();
            long askChange = totalAskQty - prev.askQty();
            ofi = bidChange - askChange;
            
            if (ofi > 0) {
                log.debug("[OFI-POSITIVE] Symbol: {}, OFI: {}", symbol, ofi);
            }
        }

        prevQuotes.put(symbol, new QuoteState(totalBidQty, totalAskQty));
        return ofi;
    }

    private record QuoteState(long bidQty, long askQty) {}
}
