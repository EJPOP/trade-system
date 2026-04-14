package top.tradesystem.krx.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import top.tradesystem.krx.config.KisProperties;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class KisOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(KisOpenApiClient.class);
    private final WebClient webClient;
    private final KisProperties props;
    private final ObjectMapper objectMapper;
    private String accessToken;

    public KisOpenApiClient(WebClient.Builder builder, KisProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.webClient = builder.baseUrl(props.baseUrl()).build();
        loadTokenFromFile();
    }

    public Mono<String> postOrder(boolean isBuy, String symbol, int qty, long price, String ordDvsn) {
        String trId = isBuy ? "TTTC0841U" : "TTTC0802U"; 
        Map<String, Object> body = new HashMap<>();
        body.put("CANO", props.accountNo().substring(0, 8));
        body.put("ACNT_PRDT_CD", props.accountNo().substring(8));
        body.put("PDNO", symbol);
        body.put("ORD_DVSN", ordDvsn); 
        body.put("ORD_QTY", String.valueOf(qty));
        body.put("ORD_UNPR", String.valueOf(price));

        return ensureToken()
                .flatMap(token -> webClient.post()
                        .uri("/uapi/domestic-stock/v1/trading/order-cash")
                        .header("authorization", "Bearer " + token)
                        .header("appkey", props.appKey())
                        .header("appsecret", props.appSecret())
                        .header("tr_id", trId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(res -> log.info("Order Response: {}", res)));
    }

    public Mono<String> postOrder(boolean isBuy, String symbol, int qty, long price) {
        return postOrder(isBuy, symbol, qty, price, price == 0 ? "01" : "00");
    }

    public Mono<String> getWebSocketApprovalKey() {
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", props.appKey());
        body.put("secretkey", props.appSecret());

        return webClient.post()
                .uri("/oauth2/Approval")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> String.valueOf(res.get("approval_key")));
    }

    private Mono<String> ensureToken() {
        if (accessToken != null) return Mono.just(accessToken);
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", props.appKey());
        body.put("appsecret", props.appSecret());

        return webClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KisTokenResponse.class)
                .map(res -> {
                    this.accessToken = res.accessToken();
                    saveTokenToFile(res);
                    return res.accessToken();
                });
    }

    private void saveTokenToFile(KisTokenResponse res) {
        try {
            objectMapper.writeValue(new File(props.tokenCachePath()), res);
        } catch (IOException e) {
            log.error("Failed to save token", e);
        }
    }

    private void loadTokenFromFile() {
        File file = new File(props.tokenCachePath());
        if (file.exists()) {
            try {
                KisTokenResponse res = objectMapper.readValue(file, KisTokenResponse.class);
                this.accessToken = res.accessToken();
                log.info("Loaded KIS token from cache");
            } catch (IOException e) {
                log.warn("Failed to load token cache");
            }
        }
    }

    private record KisTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") int expiresIn
    ) {}
}
