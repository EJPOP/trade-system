package top.tradesystem.krx.controller;

import top.tradesystem.krx.service.KrxTickerService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/krx/tickers")
public class KrxTickerController {

    private final KrxTickerService service;

    public KrxTickerController(KrxTickerService service) {
        this.service = service;
    }

    @GetMapping("/kospi")
    public Mono<List<Map<String, String>>> kospi(
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{8}", message = "basDd must be YYYYMMDD")
            String basDd
    ) {
        String dd = (basDd == null || basDd.isBlank()) ? service.guessLatestBusinessDay() : basDd;
        return service.getKospi(dd);
    }

    @GetMapping("/kosdaq")
    public Mono<List<Map<String, String>>> kosdaq(
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{8}", message = "basDd must be YYYYMMDD")
            String basDd
    ) {
        String dd = (basDd == null || basDd.isBlank()) ? service.guessLatestBusinessDay() : basDd;
        return service.getKosdaq(dd);
    }

    @GetMapping("/all")
    public Mono<List<Map<String, String>>> all(
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{8}", message = "basDd must be YYYYMMDD")
            String basDd
    ) {
        String dd = (basDd == null || basDd.isBlank()) ? service.guessLatestBusinessDay() : basDd;
        return service.getAll(dd);
    }
}
