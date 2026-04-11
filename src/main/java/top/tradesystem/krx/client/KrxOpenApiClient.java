package top.tradesystem.krx.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.config.KrxProperties;
import top.tradesystem.krx.dto.BasDdRequest;
import top.tradesystem.krx.exception.KrxApiException;
import top.tradesystem.krx.dto.Market;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        };
        return postForOutBlock1List(path, new BasDdRequest(basDd));
    }

    public Mono<List<Map<String, String>>> fetchDailyTrade(String basDd, Market market) {
        String path = switch (market) {
            case KOSPI -> PATH_STK_BYDD_TRD;
            case KOSDAQ -> PATH_KSQ_BYDD_TRD;
        };
        return postForOutBlock1List(path, new BasDdRequest(basDd));
    }

    public Mono<List<Map<String, String>>> fetchIndexDailyPrice(String basDd, Market market) {
        String path = switch (market) {
            case KOSPI -> PATH_KOSPI_DD_TRD;
            case KOSDAQ -> PATH_KOSDAQ_DD_TRD;
        };
        return postForOutBlock1List(path, new BasDdRequest(basDd));
    }

    private Mono<List<Map<String, String>>> postForOutBlock1List(String path, Object body) {
        return webClient
                .post()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new KrxApiException(
                                        "KRX API " + response.statusCode().value() + " on " + path + ": " + b.substring(0, Math.min(b.length(), 200)),
                                        response.statusCode().value()
                                )))
                .bodyToMono(byte[].class)
                .map(this::decodeKrxJson)
                .map(json -> {
                    try {
                        Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
                        return parseOutBlock1FromMap(root, path, body);
                    } catch (Exception e) {
                        throw new RuntimeException("Parse error on " + path, e);
                    }
                });
    }

    private List<Map<String, String>> parseOutBlock1FromMap(Map<String, Object> root, String path, Object body) {
        String[] candidates = {"OutBlock_1", "OUTBLOCK_1", "outBlock1", "output", "data"};
        for (String k : candidates) {
            Object v = root.get(k);
            if (v instanceof List) {
                return (List<Map<String, String>>) v;
            }
        }
        throw new RuntimeException("No valid OutBlock_1 in KRX response: " + path);
    }

    private String decodeKrxJson(byte[] bytes) {
        List<Charset> candidates = new ArrayList<>();
        candidates.add(StandardCharsets.UTF_8);
        try {
            candidates.add(Charset.forName("MS949"));
        } catch (Exception ignored) {}
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
}
