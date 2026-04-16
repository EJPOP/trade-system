package top.tradesystem.krx.trading.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.tradesystem.krx.trading.service.TenbaggerQuantService;
import top.tradesystem.krx.service.TradingStrategyService; // ✅ 추가

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trading/menu")
public class TradingMenuController {

    private final TenbaggerQuantService tenbaggerService;
    private final TradingStrategyService strategyService; // ✅ 추가

    public TradingMenuController(TenbaggerQuantService tenbaggerService, TradingStrategyService strategyService) {
        this.tenbaggerService = tenbaggerService;
        this.strategyService = strategyService;
    }

    /**
     * [메뉴 1] 단기: 리퀴디티 헌팅 (OFI 기반)
     * - 실시간 로그로 확인 가능하도록 설계됨
     */

    /**
     * [메뉴 2] 중기: 텐베거 텐버거 포착 (CAN SLIM 2.0)
     * RS Rating 90+ 및 수급 상위 종목 추출
     */
    @GetMapping("/tenbagger-top5")
    public List<Map<String, Object>> getTenbaggerTop5() {
        return tenbaggerService.getTopTenbaggerCandidates(5);
    }

    /**
     * [메뉴 3] 최적 자산 비중 계산 (켈리 공식)
     */
    @GetMapping("/kelly-weight")
    public double getKellyWeight(@RequestParam double winRate, @RequestParam double ratio) {
        return tenbaggerService.calculateKellyWeight(winRate, ratio);
    }
}
