package top.tradesystem.krx.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.KrxIsuBaseInfoRequest;
import top.tradesystem.krx.service.KrxTickerService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/svc/apis/sto", produces = MediaType.APPLICATION_JSON_VALUE)
public class KrxTickerController {

    private final KrxTickerService service;

    public KrxTickerController(KrxTickerService service) {
        this.service = service;
    }

    @PostMapping(value = "/stk_isu_base_info", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, String>>>> kospi(@Valid @RequestBody KrxIsuBaseInfoRequest request) {
        return service.fetchKospi(request.basDd())
                .map(rows -> Map.of("OutBlock_1", rows));
    }

    @PostMapping(value = "/ksq_isu_base_info", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, List<Map<String, String>>>> kosdaq(@Valid @RequestBody KrxIsuBaseInfoRequest request) {
        return service.fetchKosdaq(request.basDd())
                .map(rows -> Map.of("OutBlock_1", rows));
    }

    @PostMapping("/isu_base_info/range")
    public Mono<KrxTickerService.RangeFetchResult> range(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "ALL") String market,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return service.fetchRange(from, to, market, delayMs);
    }
}
