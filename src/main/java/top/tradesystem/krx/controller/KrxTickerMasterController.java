package top.tradesystem.krx.controller;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.KrxTickerMasterRow;
import top.tradesystem.krx.service.KrxTickerMasterSyncService;

import java.util.List;

@RestController
@RequestMapping("/api/krx/tickers/master")
public class KrxTickerMasterController {

    private static final String JSON_UTF8 = "application/json; charset=UTF-8";

    private final KrxTickerMasterSyncService syncService;

    public KrxTickerMasterController(KrxTickerMasterSyncService syncService) {
        this.syncService = syncService;
    }

    // ✅ 저장: POST /api/krx/tickers/master/sync?basDd=20260120&market=KOSPI|KOSDAQ|ALL
    @PostMapping(value = "/sync", produces = JSON_UTF8)
    public Mono<KrxTickerMasterSyncService.SyncResult> sync(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return syncService.sync(basDd, market);
    }

    // ✅ 공용 조회: GET /api/krx/tickers/master?market=KOSPI|KOSDAQ
    @GetMapping(produces = JSON_UTF8)
    public Mono<List<KrxTickerMasterRow>> list(@RequestParam(defaultValue = "KOSPI") String market) {
        return syncService.findByMarket(market.toUpperCase());
    }

    // ✅ KOSPI 전용 조회
    @GetMapping(value = "/kospi", produces = JSON_UTF8)
    public Mono<List<KrxTickerMasterRow>> kospi() {
        return syncService.findByMarket("KOSPI");
    }

    // ✅ KOSDAQ 전용 조회
    @GetMapping(value = "/kosdaq", produces = JSON_UTF8)
    public Mono<List<KrxTickerMasterRow>> kosdaq() {
        return syncService.findByMarket("KOSDAQ");
    }

    // ✅ 종목코드 단건 조회: GET /api/krx/tickers/master/{code}
    @GetMapping(value = "/{code}", produces = JSON_UTF8)
    public Mono<KrxTickerMasterRow> getByCode(@PathVariable String code) {
        return syncService.findByCode(code);
    }
}
