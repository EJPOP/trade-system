package top.tradesystem.krx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.tradesystem.krx.dto.KrxDailyTradeRow;
import top.tradesystem.krx.repository.KrxDailyTradeMapper;

import java.util.List;

@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);
    private final KrxDailyTradeMapper dailyTradeMapper;

    public BacktestService(KrxDailyTradeMapper dailyTradeMapper) {
        this.dailyTradeMapper = dailyTradeMapper;
    }

    /**
     * 특정 종목에 대한 "돌파 전략" 백테스트 실행
     */
    public BacktestResult runBacktest(String symbol) {
        List<KrxDailyTradeRow> history = dailyTradeMapper.findBySymbolOrderByBasDd(symbol);
        
        int totalTrades = 0;
        int winCount = 0;
        double totalProfit = 0.0;

        for (int i = 5; i < history.size(); i++) {
            KrxDailyTradeRow prev = history.get(i - 1);
            KrxDailyTradeRow current = history.get(i);

            // 1. 변동성 수축 조건 (최근 3일 고가/저가 폭이 5% 이내)
            double volatility = (getMaxHigh(history, i-3, i-1) - getMinLow(history, i-3, i-1)) / prev.clpr();
            
            // 2. 거래량 돌파 조건 (전일 거래량 대비 150% 이상)
            boolean volumeSpike = current.trdv() > prev.trdv() * 1.5;

            if (volatility < 0.05 && volumeSpike && current.clpr() > prev.clpr()) {
                totalTrades++;
                
                // 단순화된 백테스트: 당일 종가에 사서 다음날 시가/종가 흐름을 본다고 가정
                // 실제 스캘핑 백테스트는 분봉 데이터가 필요하나, 여기서는 일봉으로 경향성만 파악
                double profit = (double)(current.clpr() - prev.clpr()) / prev.clpr();
                
                if (profit > 0.01) { // 1% 이상 수익 시 승리
                    winCount++;
                }
                totalProfit += profit;
            }
        }

        double winRate = totalTrades == 0 ? 0 : (double) winCount / totalTrades * 100;
        return new BacktestResult(symbol, totalTrades, winCount, winRate, totalProfit);
    }

    private double getMaxHigh(List<KrxDailyTradeRow> list, int start, int end) {
        return list.subList(start, end + 1).stream().mapToDouble(KrxDailyTradeRow::hgpr).max().orElse(0);
    }

    private double getMinLow(List<KrxDailyTradeRow> list, int start, int end) {
        return list.subList(start, end + 1).stream().mapToDouble(KrxDailyTradeRow::lwpr).min().orElse(0);
    }

    public record BacktestResult(
            String symbol,
            int totalTrades,
            int winCount,
            double winRate,
            double totalProfit
    ) {}
}
