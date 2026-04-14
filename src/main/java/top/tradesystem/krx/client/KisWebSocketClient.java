package top.tradesystem.krx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.tradesystem.krx.config.KisProperties;
import top.tradesystem.krx.service.TradingStrategyService;

import java.util.HashMap;
import java.util.Map;

@Component
public class KisWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(KisWebSocketClient.class);
    private final KisProperties props;
    private final KisOpenApiClient apiClient;
    private final TradingStrategyService strategyService;
    private final ObjectMapper objectMapper;

    public KisWebSocketClient(KisProperties props, KisOpenApiClient apiClient, TradingStrategyService strategyService, ObjectMapper objectMapper) {
        this.props = props;
        this.apiClient = apiClient;
        this.strategyService = strategyService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void connect() {
        apiClient.getWebSocketApprovalKey().subscribe(approvalKey -> {
            StandardWebSocketClient client = new StandardWebSocketClient();
            client.execute(new TextWebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.info("Connected to KIS WebSocket. Approval Key: {}", approvalKey);
                    strategyService.getTargetSymbols(20).forEach(symbol -> {
                        try {
                            subscribeTick(session, symbol, approvalKey);
                            subscribeOrderbook(session, symbol, approvalKey);
                        } catch (Exception e) {
                            log.error("Subscription failed for {}", symbol);
                        }
                    });
                }

                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                    String payload = message.getPayload();
                    if (payload.startsWith("0") || payload.startsWith("1")) {
                        String[] parts = payload.split("\\|");
                        if (parts.length >= 4) {
                            String trId = parts[1];
                            String[] data = parts[3].split("\\^");
                            if ("H0STCNT0".equals(trId)) {
                                strategyService.processRealtimeTick(data[0], Long.parseLong(data[2]), Long.parseLong(data[7]), Double.parseDouble(data[22]));
                            } else if ("H0STASP0".equals(trId)) {
                                strategyService.processRealtimeOFI(data[0], Long.parseLong(data[23]), Long.parseLong(data[12]));
                            }
                        }
                    } else if (payload.contains("PINGPONG")) {
                        session.sendMessage(new TextMessage("PONG"));
                    }
                }
            }, props.websocketUrl());
        });
    }

    private void subscribeTick(WebSocketSession session, String symbol, String approvalKey) throws Exception {
        Map<String, Object> req = createSubReq("H0STCNT0", symbol, approvalKey);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(req)));
    }

    private void subscribeOrderbook(WebSocketSession session, String symbol, String approvalKey) throws Exception {
        Map<String, Object> req = createSubReq("H0STASP0", symbol, approvalKey);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(req)));
    }

    private Map<String, Object> createSubReq(String trId, String symbol, String approvalKey) {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> header = new HashMap<>();
        header.put("approval_key", approvalKey);
        header.put("custtype", "P");
        header.put("tr_type", "1");
        header.put("content-type", "utf-8");

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("tr_id", trId);
        input.put("tr_key", symbol);
        body.put("input", input);

        req.put("header", header);
        req.put("body", body);
        return req;
    }
}
