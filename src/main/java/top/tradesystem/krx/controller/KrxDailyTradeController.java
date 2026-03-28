package top.tradesystem.krx.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.BasDdRequest;
import top.tradesystem.krx.dto.KrxDailyTradeRow;
import top.tradesystem.krx.dto.Market;
import top.tradesystem.krx.service.KrxDailyTradeService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/svc/apis/sto", produces = MediaType.APPLICATION_JSON_VALUE)
public class KrxDailyTradeController {

    private final KrxDailyTradeService service;

    public KrxDailyTradeController(KrxDailyTradeService service) {
        this.service = service;
    }

    @PostMapping("/sync")
    public Mono<KrxDailyTradeService.SyncResult> sync(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "ALL") String market
    ) {
        validateBasDd(basDd);
        return service.sync(basDd, market);
    }

    @PostMapping("/sync-range")
    public Mono<KrxDailyTradeService.RangeSyncResult> syncRange(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "ALL") String market,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        validateBasDd(from);
        validateBasDd(to);
        return service.syncRange(from, to, market, delayMs);
    }

    private void validateBasDd(String basDd) {
        if (basDd == null || !basDd.matches("\\d{8}")) {
            throw new IllegalArgumentException("basDd must be YYYYMMDD format: " + basDd);
        }
    }

    @GetMapping("/find")
    public Mono<List<KrxDailyTradeRow>> find(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return service.findByBasDdAndMarket(basDd, market);
    }

    @PostMapping(value = "/stk_bydd_trd", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, Object>>>> kospi(@Valid @RequestBody BasDdRequest request) {
        return service.fetchDailyTradeFromApi(request.basDd(), Market.KOSPI)
                .map(rows -> Map.of("OutBlock_1", rows));
    }

    @PostMapping(value = "/ksq_bydd_trd", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, Object>>>> kosdaq(@Valid @RequestBody BasDdRequest request) {
        return service.fetchDailyTradeFromApi(request.basDd(), Market.KOSDAQ)
                .map(rows -> Map.of("OutBlock_1", rows));
    }
}
