package top.tradesystem.krx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import top.tradesystem.krx.client.KrxOpenApiClient;
import top.tradesystem.krx.dto.KrxIndexDailyPriceRow;
import top.tradesystem.krx.dto.Market;
import top.tradesystem.krx.repository.KrxIndexDailyPriceMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KrxDailyPriceService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Logger log = LoggerFactory.getLogger(KrxDailyPriceService.class);
    private static final Retry IDX_RETRY = Retry.backoff(2, Duration.ofMillis(300))
            .filter(KrxDailyPriceService::isRetryable)
            .doBeforeRetry(sig -> log.warn(
                    "idx sync retry. attempt={}, message={}",
                    sig.totalRetries() + 1,
                    sig.failure() == null ? "" : sig.failure().getMessage()
            ));

    private final KrxOpenApiClient client;
    private final KrxIndexDailyPriceMapper mapper;

    public KrxDailyPriceService(KrxOpenApiClient client, KrxIndexDailyPriceMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public Mono<List<Map<String, String>>> fetchIndexDailyPriceFromApi(String basDd, Market market) {
        return client.fetchIndexDailyPrice(basDd, market)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KrxIndexDailyPriceRow>> findByBasDdAndMarket(String basDd, String market) {
        return Mono.fromCallable(() -> mapper.findByBasDdAndMarket(basDd, market))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<SyncResult> sync(String basDd, String market) {
        String m = (market == null || market.isBlank()) ? "KOSPI" : market.toUpperCase(Locale.ROOT);

        return switch (m) {
            case "KOSPI" -> syncOneSafe(basDd, Market.KOSPI);
            case "KOSDAQ" -> syncOneSafe(basDd, Market.KOSDAQ);
            case "ALL" -> Mono.zip(syncOneSafe(basDd, Market.KOSPI), syncOneSafe(basDd, Market.KOSDAQ))
                    .map(t -> {
                        SyncResult kospi = t.getT1();
                        SyncResult kosdaq = t.getT2();
                        boolean failed = kospi.failed() || kosdaq.failed();
                        boolean skipped = kospi.skipped() || kosdaq.skipped();
                        String error = mergeErrors(kospi.error(), kosdaq.error());
                        return new SyncResult(
                                basDd,
                                "ALL",
                                kospi.saved() + kosdaq.saved(),
                                kospi.fetchedRows() + kosdaq.fetchedRows(),
                                kospi.upsertAffectedRows() + kosdaq.upsertAffectedRows(),
                                failed,
                                skipped,
                                error
                        );
                    });
            default -> Mono.error(new IllegalArgumentException("market must be KOSPI|KOSDAQ|ALL"));
        };
    }

    private Mono<SyncResult> syncOne(String basDd, Market market) {
        return fetchIndexDailyPriceFromApi(basDd, market)
                .retryWhen(IDX_RETRY)
                .flatMap(rows ->
                        Mono.fromCallable(() -> {
                                    List<KrxIndexDailyPriceRow> toSave = toRows(basDd, market.name(), rows);
                                    int fetchedRows = toSave.size();
                                    int upsertAffectedRows = toSave.isEmpty() ? 0 : mapper.upsertBatch(toSave);
                                    return new int[]{fetchedRows, upsertAffectedRows};
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .map(stats -> SyncResult.success(basDd, market.name(), stats[0], stats[1]));
    }

    private Mono<SyncResult> syncOneSafe(String basDd, Market market) {
        return syncOne(basDd, market)
                .onErrorResume(ex -> {
                    String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    if (isSkippable(ex, msg)) {
                        log.info("idx sync skipped. basDd={}, market={}, message={}", basDd, market.name(), msg);
                        return Mono.just(SyncResult.skipped(basDd, market.name(), msg));
                    }

                    log.warn("idx sync failed. basDd={}, market={}, message={}", basDd, market.name(), msg);
                    return Mono.just(SyncResult.failed(basDd, market.name(), msg));
                });
    }

    public Mono<RangeSyncResult> syncRange(String from, String to, String market) {
        return syncRange(from, to, market, 0L);
    }

    public Mono<RangeSyncResult> syncRange(String from, String to, String market, long delayMs) {
        LocalDate start = LocalDate.parse(from, YYYYMMDD);
        LocalDate end = LocalDate.parse(to, YYYYMMDD);
        if (end.isBefore(start)) return Mono.error(new IllegalArgumentException("to must be >= from"));
        if (delayMs < 0) return Mono.error(new IllegalArgumentException("delayMs must be >= 0"));

        String m = (market == null || market.isBlank()) ? "ALL" : market.toUpperCase(Locale.ROOT);
        Duration perDayDelay = Duration.ofMillis(delayMs);

        Flux<String> days = Flux.create(sink -> {
            LocalDate d = start;
            while (!d.isAfter(end)) {
                sink.next(d.format(YYYYMMDD));
                d = d.plusDays(1);
            }
            sink.complete();
        });

        return days.concatMap(dd ->
                        Mono.delay(perDayDelay)
                                .then(sync(dd, m))
                )
                .collectList()
                .map(list -> new RangeSyncResult(
                        from,
                        to,
                        m,
                        delayMs,
                        list.stream().mapToInt(SyncResult::saved).sum(),
                        list.stream().mapToInt(SyncResult::fetchedRows).sum(),
                        list.stream().mapToInt(SyncResult::upsertAffectedRows).sum(),
                        (int) list.stream().filter(SyncResult::failed).count(),
                        (int) list.stream().filter(SyncResult::skipped).count(),
                        list
                ));
    }

    private boolean isSkippable(Throwable ex, String message) {
        if (ex instanceof WebClientResponseException w && w.getStatusCode().value() == 403) {
            return true;
        }
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return m.contains("403 forbidden");
    }

    private static boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientRequestException) {
            return true;
        }

        if (ex instanceof WebClientResponseException w) {
            int code = w.getStatusCode().value();
            return code == 429 || code >= 500;
        }

        String m = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return m.contains("timeout") || m.contains("connection reset") || m.contains("temporarily unavailable");
    }

    private String mergeErrors(String... messages) {
        return java.util.Arrays.stream(messages)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(" | "));
    }

    private List<KrxIndexDailyPriceRow> toRows(String basDd, String market, List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        return rows.stream()
                .map(m -> KrxIndexDailyPriceRow.fromApiMap(basDd, market, m))
                .filter(Objects::nonNull)
                .toList();
    }

    public record SyncResult(
            String basDd,
            String market,
            int saved,
            int fetchedRows,
            int upsertAffectedRows,
            boolean failed,
            boolean skipped,
            String error
    ) {
        static SyncResult success(String basDd, String market, int fetchedRows, int upsertAffectedRows) {
            return new SyncResult(
                    basDd,
                    market,
                    upsertAffectedRows,
                    fetchedRows,
                    upsertAffectedRows,
                    false,
                    false,
                    null
            );
        }

        static SyncResult failed(String basDd, String market, String error) {
            return new SyncResult(basDd, market, 0, 0, 0, true, false, error);
        }

        static SyncResult skipped(String basDd, String market, String error) {
            return new SyncResult(basDd, market, 0, 0, 0, false, true, error);
        }
    }

    public record RangeSyncResult(
            String from,
            String to,
            String market,
            long delayMs,
            int totalSaved,
            int totalFetchedRows,
            int totalUpsertAffectedRows,
            int totalFailed,
            int totalSkipped,
            List<SyncResult> results
    ) {}
}
