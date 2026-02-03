package top.tradesystem.krx.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.config.KrxProperties;
import top.tradesystem.krx.dto.KrxDailyTradeRequest;
import top.tradesystem.krx.dto.KrxIsuBaseInfoRequest;
import top.tradesystem.krx.dto.Market;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.*;

@Component
public class KrxOpenApiClient {

    // KOSPI / KOSDAQ endpoint 분리 (KRX 기준)
    private static final String PATH_STK_ISU_BASE_INFO = "/stk_isu_base_info";
    private static final String PATH_KSQ_ISU_BASE_INFO = "/ksq_isu_base_info";

    private static final String PATH_STK_BYDD_TRD = "/stk_bydd_trd";
    private static final String PATH_KSQ_BYDD_TRD = "/ksq_bydd_trd";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // fallback charset (application.yml)
    private final Charset fallbackCharset;

    public KrxOpenApiClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            KrxProperties props
    ) {
        this.objectMapper = objectMapper;
        this.fallbackCharset = Charset.forName(props.responseCharset()); // e.g. "EUC-KR" or "MS949"

        this.webClient = webClientBuilder
                .baseUrl(props.baseUrl())
                // ✅ application.yml의 auth-key를 "AUTH_KEY" 헤더로 넣음
                .defaultHeader("AUTH_KEY", props.authKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // =========================
    // 1) 종목 마스터(기초정보)
    // =========================
    public Mono<List<Map<String, String>>> fetchIsuBaseInfo(String basDd, Market market) {
        String path = switch (market) {
            case KOSPI -> PATH_STK_ISU_BASE_INFO;
            case KOSDAQ -> PATH_KSQ_ISU_BASE_INFO;
            default -> throw new IllegalArgumentException("Unsupported market: " + market);
        };
        return postForOutBlock1List(path, new KrxIsuBaseInfoRequest(basDd));
    }

    // =========================
    // 2) 일별 매매 정보
    // =========================
    public Mono<List<Map<String, String>>> fetchDailyTrade(String basDd, Market market) {
        String path = switch (market) {
            case KOSPI -> PATH_STK_BYDD_TRD;
            case KOSDAQ -> PATH_KSQ_BYDD_TRD;
            default -> throw new IllegalArgumentException("Unsupported market: " + market);
        };
        return postForOutBlock1List(path, new KrxDailyTradeRequest(basDd));
    }

    // =========================
    // 공통: byte[]로 받아 "자동 디코딩" 후
    //      JSON이 [..] 배열이든 {OutBlock_1:[..]}든 처리
    // =========================
    private Mono<List<Map<String, String>>> postForOutBlock1List(String path, Object body) {
        return webClient
                .post()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> decodeKrxJson(bytes)) // ✅ 여기서 자동 디코딩
                .map(json -> parseOutBlock1OrArray(json, path, body));
    }

    /**
     * ✅ 응답이 UTF-8인지 MS949/EUC-KR인지 섞여오는 케이스 대응
     * - UTF-8 먼저 "엄격 디코딩(에러면 실패)" 시도
     * - 실패하면 MS949 → application.yml fallbackCharset 순으로 시도
     */
    private String decodeKrxJson(byte[] bytes) {
        List<Charset> candidates = new ArrayList<>();
        candidates.add(StandardCharsets.UTF_8);

        // Windows 계열/한글 응답에서 흔한 후보
        try { candidates.add(Charset.forName("MS949")); } catch (Exception ignored) {}
        // yml fallback (EUC-KR 등)
        candidates.add(fallbackCharset);

        for (Charset cs : candidates) {
            String s = tryDecodeStrict(bytes, cs);
            if (s != null && looksLikeJson(s)) {
                return s;
            }
        }

        // 그래도 못 맞추면 그냥 fallbackCharset로 "느슨" 디코딩
        return new String(bytes, fallbackCharset);
    }

    private String tryDecodeStrict(byte[] bytes, Charset cs) {
        try {
            CharsetDecoder dec = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);

            CharBuffer cb = dec.decode(ByteBuffer.wrap(bytes));
            return cb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private List<Map<String, String>> parseOutBlock1OrArray(String json, String path, Object body) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 1) 응답이 [ ... ] 형태면 그대로 파싱
            if (root.isArray()) {
                return objectMapper.readValue(
                        json,
                        new TypeReference<List<Map<String, String>>>() {}
                );
            }

            // 2) 응답이 { ... } 형태면 OutBlock_1 / outBlock1 / OutBlock1 등 후보 키를 찾아 파싱
            if (root.isObject()) {
                String[] candidates = {
                        "OutBlock_1", "OUTBLOCK_1", "outBlock1", "out_block1",
                        "OutBlock1", "output", "result", "data"
                };

                for (String k : candidates) {
                    JsonNode n = root.get(k);
                    if (n != null && n.isArray()) {
                        return objectMapper.convertValue(
                                n,
                                new TypeReference<List<Map<String, String>>>() {}
                        );
                    }
                }

                // 3) 그래도 없으면: "배열처럼 생긴 첫 array 필드"를 하나 찾아서 반환 (KRX 변형 대응)
                for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> e = it.next();
                    if (e.getValue() != null && e.getValue().isArray()) {
                        return objectMapper.convertValue(
                                e.getValue(),
                                new TypeReference<List<Map<String, String>>>() {}
                        );
                    }
                }
            }

            // 여기까지 오면 파싱 불가
            throw new RuntimeException("KRX JSON 구조가 예상과 다릅니다. path=" + path + ", body=" + body
                    + ", rootType=" + root.getNodeType());

        } catch (Exception e) {
            String head = json == null ? "" : json.substring(0, Math.min(json.length(), 300));
            throw new RuntimeException("KRX 응답 JSON 파싱 실패. path=" + path + ", body=" + body + ", head=" + head, e);
        }
    }

    // 일별 시세(OHLC 등) - 현재 sto/*_bydd_trd 응답에 시세/거래정보가 함께 포함되므로 동일 호출 재사용
    public Mono<List<Map<String, String>>> fetchDailyPrice(String basDd, Market market) {
        return fetchDailyTrade(basDd, market);
    }

}
