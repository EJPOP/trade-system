package top.tradesystem.krx.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.tradesystem.krx.client.KrxOpenApiClient;
import top.tradesystem.krx.dto.KrxDailyTradeRow;
import top.tradesystem.krx.dto.Market;
import top.tradesystem.krx.repository.KrxDailyTradeMapper;

import java.time.LocalDate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KrxDailyTradeService extends BaseSyncService {

    private final KrxOpenApiClient client;
    private final KrxDailyTradeMapper mapper;

    public KrxDailyTradeService(KrxOpenApiClient client, KrxDailyTradeMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public Mono<List<Map<String, Object>>> fetchDailyTradeFromApi(String basDd, Market market) {
        return client.fetchDailyTrade(basDd, market)
                .map(list -> list.stream()
                        .map(m -> {
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.putAll(m);
                            return out;
                        })
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KrxDailyTradeRow>> findByBasDdAndMarket(String basDd, String market) {
        return dbCall(() -> mapper.findByBasDdAndMarket(basDd, market));
    }

    public Mono<List<KrxDailyTradeRow>> kospi(String basDd) {
        return findByBasDdAndMarket(basDd, "KOSPI");
    }

    public Mono<List<KrxDailyTradeRow>> kosdaq(String basDd) {
        return findByBasDdAndMarket(basDd, "KOSDAQ");
    }

    public Mono<KrxDailyTradeRow> findByBasDdAndCode(String basDd, String code) {
        return dbCall(() -> mapper.findByBasDdAndCode(basDd, code));
    }

    public Mono<SyncResult> sync(String basDd, String market) {
        String m = normalizeMarket(market, "KOSPI");

        return switch (m) {
            case "KOSPI" -> syncOne(basDd, Market.KOSPI);
            case "KOSDAQ" -> syncOne(basDd, Market.KOSDAQ);
            case "ALL" -> Mono.zip(
                    syncOne(basDd, Market.KOSPI),
                    syncOne(basDd, Market.KOSDAQ)
            ).map(t -> new SyncResult(
                    basDd,
                    "ALL",
                    t.getT1().saved() + t.getT2().saved(),
                    t.getT1().skipped() && t.getT2().skipped()
            ));
            default -> Mono.error(new IllegalArgumentException("market must be KOSPI|KOSDAQ|ALL"));
        };
    }

    private Mono<SyncResult> syncOne(String basDd, Market market) {
        final String mk = market.name();

        Mono<Boolean> alreadyExists = dbCall(() -> mapper.countByBasDdAndMarket(basDd, mk) > 0);

        return alreadyExists.flatMap(exists -> {
            if (exists) {
                return Mono.just(new SyncResult(basDd, mk, 0, true));
            }

            return fetchDailyTradeFromApi(basDd, market)
                    .map(rows -> toTradeRows(basDd, rows))
                    .flatMap((List<KrxDailyTradeRow> toSave) ->
                            dbCall(() -> {
                                if (toSave == null || toSave.isEmpty()) return 0;
                                return mapper.upsertBatch(toSave);
                            })
                    )
                    .map(saved -> new SyncResult(basDd, mk, saved, false));
        });
    }

    public Mono<RangeSyncResult> syncRange(String from, String to, String market) {
        return syncRange(from, to, market, 0L);
    }

    public Mono<RangeSyncResult> syncRange(String from, String to, String market, long delayMs) {
        LocalDate start = LocalDate.parse(from, BAS_DD_FMT);
        LocalDate end = LocalDate.parse(to, BAS_DD_FMT);

        Mono<Void> validation = validateRange(start, end, delayMs);

        String m = normalizeMarket(market, "KOSPI");
        Duration perDayDelay = Duration.ofMillis(delayMs);

        return validation.then(
                generateDateRange(start, end)
                        .concatMap(dd -> Mono.delay(perDayDelay).then(sync(dd, m)))
                        .collectList()
                        .map(list -> {
                            int totalSaved = list.stream().mapToInt(SyncResult::saved).sum();
                            int totalSkipped = (int) list.stream().filter(SyncResult::skipped).count();
                            return new RangeSyncResult(from, to, m, delayMs, totalSaved, totalSkipped, list);
                        })
        );
    }

    private List<KrxDailyTradeRow> toTradeRows(String basDd, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        return rows.stream()
                .map(m -> KrxDailyTradeRow.fromApiMap(basDd, m))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public record SyncResult(String basDd, String market, int saved, boolean skipped) {
    }

    public record RangeSyncResult(
            String from,
            String to,
            String market,
            long delayMs,
            int totalSaved,
            int totalSkipped,
            List<SyncResult> results
    ) {
    }
}
