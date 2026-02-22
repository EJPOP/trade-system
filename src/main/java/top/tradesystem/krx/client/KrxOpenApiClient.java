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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class KrxOpenApiClient {

    private static final String PATH_STK_ISU_BASE_INFO = "/svc/apis/sto/stk_isu_base_info";
    private static final String PATH_KSQ_ISU_BASE_INFO = "/svc/apis/sto/ksq_isu_base_info";

    private static final String PATH_STK_BYDD_TRD = "/svc/apis/sto/stk_bydd_trd";
    private static final String PATH_KSQ_BYDD_TRD = "/svc/apis/sto/ksq_bydd_trd";

    private static final String PATH_KOSPI_DD_TRD = "/svc/apis/idx/kospi_dd_trd";
    private static final String PATH_KOSDAQ_DD_TRD = "/svc/apis/idx/kosdaq_dd_trd";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Charset fallbackCharset;

    public KrxOpenApiClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            KrxProperties props
    ) {
        this.objectMapper = objectMapper;
        this.fallbackCharset = Charset.forName(props.responseCharset());

        this.webClient = webClientBuilder
                .baseUrl(props.baseUrl())
                .defaultHeader("AUTH_KEY", props.authKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<List<Map<String, String>>> fetchIsuBaseInfo(String basDd, Market market) {
        String path = switch (market) {
            case KOSPI -> PATH_STK_ISU_BASE_INFO;
            case KOSDAQ -> PATH_KSQ_ISU_BASE_INFO;
            default -> throw new IllegalArgumentException("Unsupported market: " + market);
        };
        return postForOutBlock1List(path, new KrxIsuBaseInfoRequest(basDd));
    }

    public Mono<List<Map<String, String>>> fetchDailyTrade(String basDd, Market market) {
        String path = switch (market) {
            case KOSPI -> PATH_STK_BYDD_TRD;
            case KOSDAQ -> PATH_KSQ_BYDD_TRD;
            default -> throw new IllegalArgumentException("Unsupported market: " + market);
        };
        return postForOutBlock1List(path, new KrxDailyTradeRequest(basDd));
    }

    public Mono<List<Map<String, String>>> fetchIndexDailyPrice(String basDd, Market market) {
        String path = switch (market) {
            case KOSPI -> PATH_KOSPI_DD_TRD;
            case KOSDAQ -> PATH_KOSDAQ_DD_TRD;
            default -> throw new IllegalArgumentException("Unsupported market: " + market);
        };
        return postForOutBlock1List(path, new KrxDailyTradeRequest(basDd));
    }

    // Backward-compatible alias.
    public Mono<List<Map<String, String>>> fetchDailyPrice(String basDd, Market market) {
        return fetchDailyTrade(basDd, market);
    }

    private Mono<List<Map<String, String>>> postForOutBlock1List(String path, Object body) {
        return webClient
                .post()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(this::decodeKrxJson)
                .map(json -> parseOutBlock1OrArray(json, path, body));
    }

    /**
     * KRX responses can be UTF-8 or Korean legacy encodings.
     * Try strict UTF-8 and common Korean encodings, then fallback.
     */
    private String decodeKrxJson(byte[] bytes) {
        List<Charset> candidates = new ArrayList<>();
        candidates.add(StandardCharsets.UTF_8);
        try {
            candidates.add(Charset.forName("MS949"));
        } catch (Exception ignored) {
        }
        candidates.add(fallbackCharset);

        for (Charset cs : candidates) {
            String s = tryDecodeStrict(bytes, cs);
            if (s != null && looksLikeJson(s)) {
                return s;
            }
        }

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

            if (root.isArray()) {
                return objectMapper.readValue(
                        json,
                        new TypeReference<List<Map<String, String>>>() {
                        }
                );
            }

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
                                new TypeReference<List<Map<String, String>>>() {
                                }
                        );
                    }
                }

                for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> e = it.next();
                    if (e.getValue() != null && e.getValue().isArray()) {
                        return objectMapper.convertValue(
                                e.getValue(),
                                new TypeReference<List<Map<String, String>>>() {
                                }
                        );
                    }
                }
            }

            throw new RuntimeException(
                    "Unexpected KRX JSON structure. path=" + path + ", body=" + body + ", rootType=" + root.getNodeType()
            );
        } catch (Exception e) {
            String head = json == null ? "" : json.substring(0, Math.min(json.length(), 300));
            throw new RuntimeException(
                    "Failed to parse KRX JSON. path=" + path + ", body=" + body + ", head=" + head,
                    e
            );
        }
    }
}
