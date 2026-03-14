package top.tradesystem.krx.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.dto.KrxTickerMasterRow;
import top.tradesystem.krx.repository.KrxTickerMasterMapper;

import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KrxTickerMasterSyncService extends BaseSyncService {

    private static final DateTimeFormatter KRX_YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final KrxTickerService krxTickerService;
    private final KrxTickerMasterMapper mapper;

    public KrxTickerMasterSyncService(KrxTickerService krxTickerService, KrxTickerMasterMapper mapper) {
        this.krxTickerService = krxTickerService;
        this.mapper = mapper;
    }

    public Mono<SyncResult> sync(String basDd, String market) {
        Mono<List<Map<String, String>>> source = switch (market.toUpperCase()) {
            case "KOSPI" -> krxTickerService.getKospi(basDd);
            case "KOSDAQ" -> krxTickerService.getKosdaq(basDd);
            case "ALL" -> krxTickerService.getAll(basDd);
            default -> Mono.error(new IllegalArgumentException("market must be KOSPI|KOSDAQ|ALL"));
        };

        return source
                .map(this::toRows)
                .flatMap(rows ->
                        dbCall(() -> {
                            if (rows.isEmpty()) return new SyncResult(0, 0);
                            int affected = mapper.upsertBatch(rows);
                            return new SyncResult(rows.size(), affected);
                        })
                );
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

        return validation.then(
                generateDateRange(start, end)
                        .concatMap(dd -> Mono.delay(perDayDelay).then(sync(dd, m)))
                        .collectList()
                        .map(list -> new RangeSyncResult(
                                from,
                                to,
                                m,
                                delayMs,
                                list.stream().mapToInt(SyncResult::fetched).sum(),
                                list.stream().mapToInt(SyncResult::affected).sum(),
                                list
                        ))
        );
    }

    public Mono<KrxTickerMasterRow> findByCode(String code) {
        return dbCall(() -> mapper.findByCode(code));
    }

    public Mono<List<KrxTickerMasterRow>> findByMarket(String market) {
        return dbCall(() -> mapper.findByMarket(market));
    }

    private List<KrxTickerMasterRow> toRows(List<Map<String, String>> maps) {
        List<KrxTickerMasterRow> rows = new ArrayList<>(maps.size());
        for (Map<String, String> r : maps) {
            String code = val(r, "ISU_SRT_CD");
            if (code == null || code.isBlank()) continue;

            rows.add(new KrxTickerMasterRow(
                    code,
                    val(r, "ISU_CD"),
                    val(r, "ISU_NM"),
                    val(r, "ISU_ABBRV"),
                    val(r, "ISU_ENG_NM"),
                    val(r, "MKT_TP_NM"),
                    val(r, "SECUGRP_NM"),
                    val(r, "KIND_STKCERT_TP_NM"),
                    parseDate(val(r, "LIST_DD")),
                    val(r, "PARVAL"),
                    val(r, "LIST_SHRS")
            ));
        }
        return rows;
    }

    private static String val(Map<String, String> r, String key) {
        String v = r.get(key);
        return v == null ? null : v.trim();
    }

    private static LocalDate parseDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) return null;
        try {
            return LocalDate.parse(yyyymmdd.trim(), KRX_YYYYMMDD);
        } catch (Exception e) {
            return null;
        }
    }

    public record SyncResult(int fetched, int affected) {}

    public record RangeSyncResult(
            String from,
            String to,
            String market,
            long delayMs,
            int totalFetched,
            int totalAffected,
            List<SyncResult> results
    ) {}
}
