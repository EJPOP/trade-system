package top.tradesystem.krx.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.KrxTickerMasterRow;
import top.tradesystem.krx.service.KrxTickerMasterSyncService;

import java.util.List;

@RestController
@RequestMapping("/internal/krx/tickers/master")
public class KrxTickerMasterController {

    private static final String JSON_UTF8 = "application/json; charset=UTF-8";

    private final KrxTickerMasterSyncService syncService;

    public KrxTickerMasterController(KrxTickerMasterSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping(value = "/sync", produces = JSON_UTF8)
    public Mono<KrxTickerMasterSyncService.SyncResult> sync(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        validateBasDd(basDd);
        return syncService.sync(basDd, market);
    }

    @PostMapping(value = "/sync-range", produces = JSON_UTF8)
    public Mono<KrxTickerMasterSyncService.RangeSyncResult> syncRange(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "ALL") String market,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        validateBasDd(from);
        validateBasDd(to);
        return syncService.syncRange(from, to, market, delayMs);
    }

    private void validateBasDd(String basDd) {
        top.tradesystem.krx.common.DateConstants.validateBasDd(basDd);
    }

    @GetMapping(produces = JSON_UTF8)
    public Mono<List<KrxTickerMasterRow>> list(@RequestParam(defaultValue = "KOSPI") String market) {
        return syncService.findByMarket(market.toUpperCase());
    }

    @GetMapping(value = "/kospi", produces = JSON_UTF8)
    public Mono<List<KrxTickerMasterRow>> kospi() {
        return syncService.findByMarket("KOSPI");
    }

    @GetMapping(value = "/kosdaq", produces = JSON_UTF8)
    public Mono<List<KrxTickerMasterRow>> kosdaq() {
        return syncService.findByMarket("KOSDAQ");
    }

    @GetMapping(value = "/{code}", produces = JSON_UTF8)
    public Mono<KrxTickerMasterRow> getByCode(@PathVariable String code) {
        return syncService.findByCode(code);
    }
}
