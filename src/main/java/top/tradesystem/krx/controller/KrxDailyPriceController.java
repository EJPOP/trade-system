package top.tradesystem.krx.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.KrxDailyTradeRequest;
import top.tradesystem.krx.dto.Market;
import top.tradesystem.krx.service.KrxDailyPriceService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/svc/apis/idx", produces = MediaType.APPLICATION_JSON_VALUE)
public class KrxDailyPriceController {

    private final KrxDailyPriceService service;

    public KrxDailyPriceController(KrxDailyPriceService service) {
        this.service = service;
    }

    @PostMapping(value = "/kospi_dd_trd", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, String>>>> kospi(@Valid @RequestBody KrxDailyTradeRequest request) {
        return service.fetchIndexDailyPriceFromApi(request.basDd(), Market.KOSPI)
                .map(rows -> Map.of("OutBlock_1", rows));
    }

    @PostMapping(value = "/kosdaq_dd_trd", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, String>>>> kosdaq(@Valid @RequestBody KrxDailyTradeRequest request) {
        return service.fetchIndexDailyPriceFromApi(request.basDd(), Market.KOSDAQ)
                .map(rows -> Map.of("OutBlock_1", rows));
    }

    @PostMapping("/sync-range")
    public Mono<KrxDailyPriceService.RangeSyncResult> syncRange(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "ALL") String market,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return service.syncRange(from, to, market, delayMs);
    }
}
