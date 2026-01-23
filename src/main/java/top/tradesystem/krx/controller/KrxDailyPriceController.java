package top.tradesystem.krx.controller;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.KrxDailyPriceRow;
import top.tradesystem.krx.service.KrxDailyPriceService;

import java.util.List;

@RestController
@RequestMapping(value = "/api/krx/prices/daily", produces = "application/json; charset=UTF-8")
public class KrxDailyPriceController {

    private final KrxDailyPriceService service;

    public KrxDailyPriceController(KrxDailyPriceService service) {
        this.service = service;
    }

    // ✅ DB 조회: GET /api/krx/prices/daily/kospi?basDd=20260119
    @GetMapping("/kospi")
    public Mono<List<KrxDailyPriceRow>> kospi(@RequestParam String basDd) {
        return service.kospi(basDd);
    }

    // ✅ DB 조회: GET /api/krx/prices/daily/kosdaq?basDd=20260119
    @GetMapping("/kosdaq")
    public Mono<List<KrxDailyPriceRow>> kosdaq(@RequestParam String basDd) {
        return service.kosdaq(basDd);
    }

    // ✅ DB 단건 조회: GET /api/krx/prices/daily/{basDd}/{code}
    @GetMapping("/{basDd}/{code}")
    public Mono<KrxDailyPriceRow> one(@PathVariable String basDd, @PathVariable String code) {
        return service.findByBasDdAndCode(basDd, code);
    }

    // ✅ 저장(단일일자): POST /api/krx/prices/daily/sync?basDd=20260119&market=KOSPI|KOSDAQ|ALL
    @PostMapping("/sync")
    public Mono<KrxDailyPriceService.SyncResult> sync(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return service.sync(basDd, market);
    }

    // ✅ 저장(from~to): POST /api/krx/prices/daily/sync-range?from=20260101&to=20260131&market=KOSPI|KOSDAQ|ALL
    @PostMapping("/sync-range")
    public Mono<KrxDailyPriceService.RangeSyncResult> syncRange(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return service.syncRange(from, to, market);
    }
}
