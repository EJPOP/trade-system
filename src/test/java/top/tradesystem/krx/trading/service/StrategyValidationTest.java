package top.tradesystem.krx.trading.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrategyValidationTest {

    private final LiquidityHuntingService ofiService = new LiquidityHuntingService();
    private final TenbaggerQuantService kellyService = new TenbaggerQuantService(null);

    @Test
    @DisplayName("OFI(수급 불균형) 계산 로직 검증")
    void validateOFICalculation() {
        // T-1 시점 데이터 주입
        ofiService.calculateOFI("005930", 10000, 5000);
        
        // T 시점: 매수 잔량은 늘고(+2000), 매도 잔량은 줄어듬(-500)
        // OFI = (12000-10000) - (4500-5000) = 2000 - (-500) = 2500
        double ofi = ofiService.calculateOFI("005930", 12000, 4500);
        
        assertEquals(2500, ofi, "OFI 계산이 수학적으로 정확해야 합니다.");
    }

    @Test
    @DisplayName("켈리 공식(Kelly Criterion) 자산 비중 계산 검증")
    void validateKellyCriterion() {
        // 승률 60%(0.6), 손익비 2배(2.0) 가정
        // f* = (2.0 * 0.6 - 0.4) / 2.0 = (1.2 - 0.4) / 2.0 = 0.4
        double weight = kellyService.calculateKellyWeight(0.6, 2.0);
        
        assertEquals(0.4, weight, 0.001, "켈리 공식 비중 계산이 정확해야 합니다.");
    }
}
