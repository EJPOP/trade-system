package top.tradesystem.krx.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.BasDdRequest;
import top.tradesystem.krx.dto.KrxIndexDailyPriceRow;
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

    @PostMapping("/sync")
    public Mono<KrxDailyPriceService.SyncResult> sync(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "ALL") String market
    ) {
        validateBasDd(basDd);
        return service.sync(basDd, market)
                .map(r -> {
                    if (r.failed()) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "KRX sync failed: " + r.error());
                    }
                    return r;
                });
    }

    @PostMapping("/sync-range")
    public Mono<KrxDailyPriceService.RangeSyncResult> syncRange(
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
        top.tradesystem.krx.common.DateConstants.validateBasDd(basDd);
    }

    @GetMapping("/find")
    public Mono<List<KrxIndexDailyPriceRow>> find(
            @RequestParam String basDd,
            @RequestParam(defaultValue = "KOSPI") String market
    ) {
        return service.findByBasDdAndMarket(basDd, market);
    }

    @PostMapping(value = "/kospi_dd_trd", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, String>>>> kospi(@Valid @RequestBody BasDdRequest request) {
        return service.fetchIndexDailyPriceFromApi(request.basDd(), Market.KOSPI)
                .map(rows -> Map.of("OutBlock_1", rows));
    }

    @PostMapping(value = "/kosdaq_dd_trd", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, String>>>> kosdaq(@Valid @RequestBody BasDdRequest request) {
        return service.fetchIndexDailyPriceFromApi(request.basDd(), Market.KOSDAQ)
                .map(rows -> Map.of("OutBlock_1", rows));
    }
}
