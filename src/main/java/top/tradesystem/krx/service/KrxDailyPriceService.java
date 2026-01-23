package top.tradesystem.krx.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.tradesystem.krx.client.KrxOpenApiClient;
import top.tradesystem.krx.dto.KrxDailyPriceRow;
import top.tradesystem.krx.dto.Market;
import top.tradesystem.krx.repository.KrxDailyPriceMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class KrxDailyPriceService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KrxOpenApiClient client;
    private final KrxDailyPriceMapper mapper;

    public KrxDailyPriceService(KrxOpenApiClient client, KrxDailyPriceMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    // =========================
    // 1) OpenAPI 호출
    // =========================
    public Mono<List<Map<String, String>>> fetchDailyPriceFromApi(String basDd, Market market) {
        return client.fetchDailyPrice(basDd, market)
                .subscribeOn(Schedulers.boundedElastic());
    }

    // =========================
    // 2) DB 조회
    // =========================
    public Mono<List<KrxDailyPriceRow>> findByBasDdAndMarket(String basDd, String market) {
        return Mono.fromCallable(() -> mapper.findByBasDdAndMarket(basDd, market))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KrxDailyPriceRow>> kospi(String basDd) {
        return findByBasDdAndMarket(basDd, "KOSPI");
    }

    public Mono<List<KrxDailyPriceRow>> kosdaq(String basDd) {
        return findByBasDdAndMarket(basDd, "KOSDAQ");
    }

    public Mono<KrxDailyPriceRow> findByBasDdAndCode(String basDd, String code) {
        return Mono.fromCallable(() -> mapper.findByBasDdAndCode(basDd, code))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // =========================
    // 3) 저장: 단일 일자 sync
    // =========================
    public Mono<SyncResult> sync(String basDd, String market) {
        String m = (market == null || market.isBlank()) ? "KOSPI" : market.toUpperCase(Locale.ROOT);

        return switch (m) {
            case "KOSPI" -> syncOne(basDd, Market.KOSPI);
            case "KOSDAQ" -> syncOne(basDd, Market.KOSDAQ);
            case "ALL" -> Mono.zip(syncOne(basDd, Market.KOSPI), syncOne(basDd, Market.KOSDAQ))
                    .map(t -> new SyncResult(basDd, "ALL", t.getT1().saved() + t.getT2().saved()));
            default -> Mono.error(new IllegalArgumentException("market must be KOSPI|KOSDAQ|ALL"));
        };
    }

    private Mono<SyncResult> syncOne(String basDd, Market market) {
        return fetchDailyPriceFromApi(basDd, market)
                .map(rows -> toRows(basDd, market.name(), rows))
                .flatMap(toSave ->
                        Mono.fromCallable(() -> toSave.isEmpty() ? 0 : mapper.upsertBatch(toSave))
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .map(saved -> new SyncResult(basDd, market.name(), saved));
    }

    // =========================
    // 4) 저장: from~to range sync (주말 포함)
    // =========================
    public Mono<RangeSyncResult> syncRange(String from, String to, String market) {
        LocalDate start = LocalDate.parse(from, YYYYMMDD);
        LocalDate end = LocalDate.parse(to, YYYYMMDD);
        if (end.isBefore(start)) return Mono.error(new IllegalArgumentException("to must be >= from"));

        String m = (market == null || market.isBlank()) ? "KOSPI" : market.toUpperCase(Locale.ROOT);

        // Flux.generate 실수로 sink 호출 안 해서 나던 에러 방지 => Flux.create + while 루프
        Flux<String> days = Flux.create(sink -> {
            LocalDate d = start;
            while (!d.isAfter(end)) {
                sink.next(d.format(YYYYMMDD));
                d = d.plusDays(1);
            }
            sink.complete();
        });

        return days.concatMap(dd -> sync(dd, m))
                .collectList()
                .map(list -> new RangeSyncResult(
                        from,
                        to,
                        m,
                        list.stream().mapToInt(SyncResult::saved).sum(),
                        list
                ));
    }

    private List<KrxDailyPriceRow> toRows(String basDd, String market, List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        return rows.stream()
                .map(KrxDailyPriceRow::toObjectMap)
                .map(m -> KrxDailyPriceRow.fromApiMap(basDd, market, m))
                .filter(Objects::nonNull)
                .toList();
    }

    // =========================
    // DTO
    // =========================
    public record SyncResult(String basDd, String market, int saved) {}

    public record RangeSyncResult(
            String from,
            String to,
            String market,
            int totalSaved,
            List<SyncResult> results
    ) {}
}
