package top.tradesystem.krx.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.client.KrxOpenApiClient;
import top.tradesystem.krx.dto.Market;

import java.time.DayOfWeek;
import java.time.LocalDate;
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

    // =========================
    // ✅ 신규(원천) 호출 메서드
    // =========================

    /** KOSPI 종목 마스터 원천 조회 */
    public Mono<List<Map<String, String>>> fetchKospi(String basDd) {
        return client.fetchIsuBaseInfo(basDd, Market.KOSPI)
                .map(r -> r == null ? List.of() : r);
    }

    /** KOSDAQ 종목 마스터 원천 조회 */
    public Mono<List<Map<String, String>>> fetchKosdaq(String basDd) {
        return client.fetchIsuBaseInfo(basDd, Market.KOSDAQ)
                .map(r -> r == null ? List.of() : r);
    }

    /** 공용(시장 선택) 종목 마스터 원천 조회 */
    public Mono<List<Map<String, String>>> fetch(String basDd, String market) {
        Market m = Market.valueOf(market.toUpperCase());
        return client.fetchIsuBaseInfo(basDd, m)
                .map(r -> r == null ? List.of() : r);
    }

    // ==========================================
    // ✅ 기존 코드 호환용(컴파일 깨짐 방지용) 메서드
    //   - 컨트롤러/SyncService가 찾는 메서드들
    // ==========================================

    /** (기존 호환) KOSPI */
    public Mono<List<Map<String, String>>> getKospi(String basDd) {
        return fetchKospi(basDd);
    }

    /** (기존 호환) KOSDAQ */
    public Mono<List<Map<String, String>>> getKosdaq(String basDd) {
        return fetchKosdaq(basDd);
    }

    /** (기존 호환) ALL = KOSPI + KOSDAQ merge */
    public Mono<List<Map<String, String>>> getAll(String basDd) {
        return Mono.zip(fetchKospi(basDd), fetchKosdaq(basDd))
                .map(tuple -> {
                    List<Map<String, String>> merged = new ArrayList<>(tuple.getT1().size() + tuple.getT2().size());
                    merged.addAll(tuple.getT1());
                    merged.addAll(tuple.getT2());
                    return merged;
                });
    }

    /**
     * (기존 호환) basDd 미입력 시 사용할 최근 영업일 추정값.
     * - 주말만 제외(토/일) 하는 "추정" 로직
     * - KRX 휴장일(공휴일)은 여기서 정확히 맞추지 않음
     */
    public String guessLatestBusinessDay() {
        LocalDate d = LocalDate.now();
        // 오늘이 토/일이면 직전 금요일로
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) d = d.minusDays(1);
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.minusDays(2);
        return d.format(BAS_DD_FMT);
    }
}
