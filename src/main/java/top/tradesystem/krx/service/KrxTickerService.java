package top.tradesystem.krx.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.client.KrxOpenApiClient;
import top.tradesystem.krx.dto.Market;

import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KrxTickerService {

    private static final DateTimeFormatter BAS_DD_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KrxOpenApiClient client;

    public KrxTickerService(KrxOpenApiClient client) {
        this.client = client;
    }

    public Mono<List<Map<String, String>>> fetchKospi(String basDd) {
        return client.fetchIsuBaseInfo(basDd, Market.KOSPI)
                .map(r -> r == null ? List.of() : r);
    }

    public Mono<List<Map<String, String>>> fetchKosdaq(String basDd) {
        return client.fetchIsuBaseInfo(basDd, Market.KOSDAQ)
                .map(r -> r == null ? List.of() : r);
    }

    public Mono<List<Map<String, String>>> fetch(String basDd, String market) {
        Market m = Market.valueOf(market.toUpperCase());
        return client.fetchIsuBaseInfo(basDd, m)
                .map(r -> r == null ? List.of() : r);
    }

    public Mono<List<Map<String, String>>> getKospi(String basDd) {
        return fetchKospi(basDd);
    }

    public Mono<List<Map<String, String>>> getKosdaq(String basDd) {
        return fetchKosdaq(basDd);
    }

    public Mono<List<Map<String, String>>> getAll(String basDd) {
        return Mono.zip(fetchKospi(basDd), fetchKosdaq(basDd))
                .map(tuple -> {
                    List<Map<String, String>> merged = new ArrayList<>(tuple.getT1().size() + tuple.getT2().size());
                    merged.addAll(tuple.getT1());
                    merged.addAll(tuple.getT2());
                    return merged;
                });
    }

    public Mono<RangeFetchResult> fetchRange(String from, String to, String market) {
        return fetchRange(from, to, market, 0L);
    }

    public Mono<RangeFetchResult> fetchRange(String from, String to, String market, long delayMs) {
        LocalDate start = LocalDate.parse(from, BAS_DD_FMT);
        LocalDate end = LocalDate.parse(to, BAS_DD_FMT);
        if (end.isBefore(start)) {
            return Mono.error(new IllegalArgumentException("to must be >= from"));
        }
        if (delayMs < 0) {
            return Mono.error(new IllegalArgumentException("delayMs must be >= 0"));
        }

        String m = (market == null || market.isBlank()) ? "ALL" : market.toUpperCase();
        Duration perDayDelay = Duration.ofMillis(delayMs);

        Flux<String> days = Flux.create(sink -> {
            LocalDate d = start;
            while (!d.isAfter(end)) {
                sink.next(d.format(BAS_DD_FMT));
                d = d.plusDays(1);
            }
            sink.complete();
        });

        return days.concatMap(dd -> Mono.delay(perDayDelay).then(fetchByDay(dd, m)))
                .collectList()
                .map(list -> new RangeFetchResult(
                        from,
                        to,
                        m,
                        delayMs,
                        list.stream().mapToInt(DailyFetchResult::fetched).sum(),
                        list
                ));
    }

    private Mono<DailyFetchResult> fetchByDay(String basDd, String market) {
        return switch (market) {
            case "KOSPI" -> getKospi(basDd).map(rows -> new DailyFetchResult(basDd, "KOSPI", rows.size()));
            case "KOSDAQ" -> getKosdaq(basDd).map(rows -> new DailyFetchResult(basDd, "KOSDAQ", rows.size()));
            case "ALL" -> getAll(basDd).map(rows -> new DailyFetchResult(basDd, "ALL", rows.size()));
            default -> Mono.error(new IllegalArgumentException("market must be KOSPI|KOSDAQ|ALL"));
        };
    }

    public record DailyFetchResult(String basDd, String market, int fetched) {}

    public record RangeFetchResult(
            String from,
            String to,
            String market,
            long delayMs,
            int totalFetched,
            List<DailyFetchResult> results
    ) {}
}
