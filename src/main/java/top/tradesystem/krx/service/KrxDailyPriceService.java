package top.tradesystem.krx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import top.tradesystem.krx.client.KrxOpenApiClient;
import top.tradesystem.krx.dto.KrxIndexDailyPriceRow;
import top.tradesystem.krx.dto.Market;
import top.tradesystem.krx.repository.KrxIndexDailyPriceMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KrxDailyPriceService extends BaseSyncService {

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
        return dbCall(() -> mapper.findByBasDdAndMarket(basDd, market));
    }

    public Mono<SyncResult> sync(String basDd, String market) {
        String m = normalizeMarket(market, "KOSPI");
        // 요청별 403 카운터 생성 — Reactor 스레드 풀 재사용 안전
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);

        return switch (m) {
            case "KOSPI" -> syncOneSafe(basDd, Market.KOSPI, counter);
            case "KOSDAQ" -> syncOneSafe(basDd, Market.KOSDAQ, counter);
            case "ALL" -> Mono.zip(syncOneSafe(basDd, Market.KOSPI, counter), syncOneSafe(basDd, Market.KOSDAQ, counter))
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
                        dbCall(() -> {
                            List<KrxIndexDailyPriceRow> toSave = toRows(basDd, market.name(), rows);
                            int fetchedRows = toSave.size();
                            int upsertAffectedRows = toSave.isEmpty() ? 0 : mapper.upsertBatch(toSave);
                            return new int[]{fetchedRows, upsertAffectedRows};
                        })
                )
                .map(stats -> SyncResult.success(basDd, market.name(), stats[0], stats[1]));
    }

    /** sync()와 동일하지만 외부 403 카운터를 사용. syncRange에서 호출. */
    private Mono<SyncResult> syncWithCounter(String basDd, String market,
                                              java.util.concurrent.atomic.AtomicInteger counter) {
        String m = normalizeMarket(market, "KOSPI");
        return switch (m) {
            case "KOSPI" -> syncOneSafe(basDd, Market.KOSPI, counter);
            case "KOSDAQ" -> syncOneSafe(basDd, Market.KOSDAQ, counter);
            case "ALL" -> Mono.zip(syncOneSafe(basDd, Market.KOSPI, counter), syncOneSafe(basDd, Market.KOSDAQ, counter))
                    .map(t -> new SyncResult(basDd, "ALL",
                            t.getT1().saved() + t.getT2().saved(),
                            t.getT1().fetchedRows() + t.getT2().fetchedRows(),
                            t.getT1().upsertAffectedRows() + t.getT2().upsertAffectedRows(),
                            t.getT1().failed() || t.getT2().failed(),
                            t.getT1().skipped() || t.getT2().skipped(),
                            mergeErrors(t.getT1().error(), t.getT2().error())));
            default -> Mono.error(new IllegalArgumentException("market must be KOSPI|KOSDAQ|ALL"));
        };
    }

    private Mono<SyncResult> syncOneSafe(String basDd, Market market,
                                          java.util.concurrent.atomic.AtomicInteger counter403) {
        return syncOne(basDd, market)
                .doOnNext(r -> counter403.set(0))  // 성공 시 403 카운터 리셋
                .onErrorResume(ex -> {
                    String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    if (isSkippable(ex, msg, counter403)) {
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
        LocalDate start = LocalDate.parse(from, BAS_DD_FMT);
        LocalDate end = LocalDate.parse(to, BAS_DD_FMT);

        Mono<Void> validation = validateRange(start, end, delayMs);

        String m = normalizeMarket(market, "ALL");
        Duration perDayDelay = Duration.ofMillis(delayMs);
        // 배치 범위 전체에서 공유하는 403 카운터 — 날짜별 리셋 방지
        var rangeCounter = new java.util.concurrent.atomic.AtomicInteger(0);

        return validation.then(
                generateDateRange(start, end)
                        .concatMap(dd ->
                                Mono.delay(perDayDelay)
                                        .then(syncWithCounter(dd, m, rangeCounter))
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
                        ))
        );
    }

    /**
     * 403 누적 판정 — 배치 범위(syncRange) 단위로 카운터 관리.
     * Reactor 스레드 풀 재사용 문제를 피하기 위해 Mono 체인 내부에서만 사용.
     */
    private boolean isSkippable(Throwable ex, String message, java.util.concurrent.atomic.AtomicInteger counter403) {
        if (ex instanceof WebClientResponseException w && w.getStatusCode().value() == 403) {
            int count = counter403.incrementAndGet();
            if (count >= 5) {
                log.error("연속 403 {}회 — 인증키 만료 의심. 키를 확인하세요.", count);
                return false;
            }
            return true;
        }
        counter403.set(0);  // 403 아닌 응답이면 카운터 리셋
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
