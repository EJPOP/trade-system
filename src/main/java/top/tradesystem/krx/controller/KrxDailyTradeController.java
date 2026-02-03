package top.tradesystem.krx.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.KrxDailyTradeRow;
import top.tradesystem.krx.service.KrxDailyTradeService;

import java.util.List;

@RestController
@RequestMapping(value = "/api/krx/trades/daily", produces = MediaType.APPLICATION_JSON_VALUE)
public class KrxDailyTradeController {

    private final KrxDailyTradeService service;

    public KrxDailyTradeController(KrxDailyTradeService service) {
        this.service = service;
    }

    // ✅ DB 조회: GET /api/krx/trades/daily/kospi?basDd=20260119
    @GetMapping("/kospi")
    public Mono<List<KrxDailyTradeRow>> kospi(@RequestParam String basDd) {
        return service.kospi(basDd);
    }

    // ✅ DB 조회: GET /api/krx/trades/daily/kosdaq?basDd=20260119
    @GetMapping("/kosdaq")
    public Mono<List<KrxDailyTradeRow>> kosdaq(@RequestParam String basDd) {
        return service.kosdaq(basDd);
    }

    // ✅ DB 단건 조회: GET /api/krx/trades/daily/{basDd}/{code}
    @GetMapping("/{basDd}/{code}")
    public Mono<KrxDailyTradeRow> one(@PathVariable String basDd, @PathVariable String code) {
        return service.findByBasDdAndCode(basDd, code);
    }

    // ✅ 저장(단일일자): POST /api/krx/trades/daily/sync?basDd=20260119&market=KOSPI|KOSDAQ|ALL
    @PostMapping("/sync")
    public Mono<KrxDailyTradeService.SyncResult> sync(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return service.sync(basDd, market);
    }

    // ✅ 저장(from~to): POST/GET /api/krx/trades/daily/sync/range?from=20260101&to=20260131&market=KOSPI|KOSDAQ|ALL
    @GetMapping("/sync/range")
    public Mono<KrxDailyTradeService.RangeSyncResult> syncRangeGet(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return service.syncRange(from, to, market);
    }

    @PostMapping("/sync/range")
    public Mono<KrxDailyTradeService.RangeSyncResult> syncRangePost(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return service.syncRange(from, to, market);
    }

}
