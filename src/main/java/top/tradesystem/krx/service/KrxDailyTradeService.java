package top.tradesystem.krx.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.tradesystem.krx.client.KrxOpenApiClient;
import top.tradesystem.krx.dto.KrxDailyTradeRow;
import top.tradesystem.krx.dto.Market;
import top.tradesystem.krx.repository.KrxDailyTradeMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KrxDailyTradeService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KrxOpenApiClient client;
    private final KrxDailyTradeMapper mapper;

    public KrxDailyTradeService(KrxOpenApiClient client, KrxDailyTradeMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    // =========================
    // 1) KRX OpenAPI 호출 (원천)
    // =========================
    public Mono<List<Map<String, Object>>> fetchDailyTradeFromApi(String basDd, Market market) {
        // client.fetchDailyTrade: Mono<List<Map<String,String>>> 라는 전제
        return client.fetchDailyTrade(basDd, market)
                .map(list -> list.stream()
                        .map(m -> {
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.putAll(m); // String -> Object 업캐스팅
                            return out;
                        })
                        .toList()
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    // =========================
    // 2) DB 조회
    // =========================
    public Mono<List<KrxDailyTradeRow>> findByBasDdAndMarket(String basDd, String market) {
        // DB 컬럼은 market이 아니라 mkt_nm 이고, mapper XML에서 mkt_nm = #{market} 로 처리
        return Mono.fromCallable(() -> mapper.findByBasDdAndMarket(basDd, market))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KrxDailyTradeRow>> kospi(String basDd) {
        return findByBasDdAndMarket(basDd, "KOSPI");
    }

    public Mono<List<KrxDailyTradeRow>> kosdaq(String basDd) {
        return findByBasDdAndMarket(basDd, "KOSDAQ");
    }

    public Mono<KrxDailyTradeRow> findByBasDdAndCode(String basDd, String code) {
        return Mono.fromCallable(() -> mapper.findByBasDdAndCode(basDd, code))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // =========================
    // 3) 저장: 단일 일자 sync
    //    - 이미 있으면 스킵
    // =========================
    public Mono<SyncResult> sync(String basDd, String market) {
        String m = (market == null || market.isBlank()) ? "KOSPI" : market.toUpperCase(Locale.ROOT);

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

        // ✅ 0) 이미 DB에 있으면 스킵(= API 호출/저장 안 함)
        Mono<Boolean> alreadyExists = Mono.fromCallable(() -> mapper.countByBasDdAndMarket(basDd, mk) > 0)
                .subscribeOn(Schedulers.boundedElastic());

        return alreadyExists.flatMap(exists -> {
            if (exists) {
                return Mono.just(new SyncResult(basDd, mk, 0, true));
            }

            // ✅ 1) 없으면 API 호출 → 변환 → upsert
            return fetchDailyTradeFromApi(basDd, market)
                    .map(rows -> toTradeRows(basDd, rows))   // ✅ market.name() 넘기지 않음
                    .flatMap((List<KrxDailyTradeRow> toSave) ->
                            Mono.fromCallable(() -> {
                                        if (toSave == null || toSave.isEmpty()) return 0;
                                        return mapper.upsertBatch(toSave);
                                    })
                                    .subscribeOn(Schedulers.boundedElastic())
                    )
                    .map(saved -> new SyncResult(basDd, mk, saved, false));
        });
    }

    // =========================
    // 4) 저장: from~to 누적 sync
    //    - 주말/공휴일 포함
    //    - 이미 있으면 스킵
    // =========================
    public Mono<RangeSyncResult> syncRange(String from, String to, String market) {
        LocalDate start = LocalDate.parse(from, YYYYMMDD);
        LocalDate end = LocalDate.parse(to, YYYYMMDD);
        if (end.isBefore(start)) {
            return Mono.error(new IllegalArgumentException("to must be >= from"));
        }

        String m = (market == null || market.isBlank()) ? "KOSPI" : market.toUpperCase(Locale.ROOT);

        // ✅ 모든 날짜 방출 (주말/공휴일 포함)
        Flux<String> days = Flux.create(sink -> {
            LocalDate d = start;
            while (!d.isAfter(end)) {
                sink.next(d.format(YYYYMMDD));
                d = d.plusDays(1);
            }
            sink.complete();
        });

        // ✅ 날짜별 sync 순차 실행(concatMap)
        return days.concatMap(dd -> sync(dd, m))
                .collectList()
                .map(list -> {
                    int totalSaved = list.stream().mapToInt(SyncResult::saved).sum();
                    int totalSkipped = (int) list.stream().filter(SyncResult::skipped).count();
                    return new RangeSyncResult(from, to, m, totalSaved, totalSkipped, list);
                });
    }

    // =========================
    // 5) 변환: Map -> Row
    // =========================
    private List<KrxDailyTradeRow> toTradeRows(String basDd, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        return rows.stream()
                .map(m -> KrxDailyTradeRow.fromApiMap(basDd, m)) // ✅ DB 스키마 기준 DTO로 변환
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // =========================
    // DTO
    // =========================
    public record SyncResult(String basDd, String market, int saved, boolean skipped) {}

    public record RangeSyncResult(
            String from,
            String to,
            String market,
            int totalSaved,
            int totalSkipped,
            List<SyncResult> results
    ) {}

}
