package top.tradesystem.krx.client;

import com.fasterxml.jackson.databind.ObjectMapper; // ✅ 추가
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
            client.execute(new TextWebSocketHandler() { // ✅ doHandshake 대신 execute 사용 (Spring 규격)
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.info("Connected to KIS WebSocket. Approval Key: {}", approvalKey);
                    
                    strategyService.getTargetSymbols(20).forEach(symbol -> {
                        try {
                            subscribeTick(session, symbol, approvalKey);
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
                            String[] data = parts[3].split("\\^");
                            String symbol = data[0];
                            long price = Long.parseLong(data[2]);
                            long volume = Long.parseLong(data[7]);
                            double strength = Double.parseDouble(data[22]);
                            
                            strategyService.processRealtimeTick(symbol, price, volume, strength);
                        }
                    } else if (payload.contains("PINGPONG")) {
                        session.sendMessage(new TextMessage("PONG"));
                    }
                }
            }, props.websocketUrl());
        });
    }

    private void subscribeTick(WebSocketSession session, String symbol, String approvalKey) throws Exception {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> header = new HashMap<>();
        header.put("approval_key", approvalKey);
        header.put("custtype", "P");
        header.put("tr_type", "1"); // 1: 등록, 2: 해제
        header.put("content-type", "utf-8");

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("tr_id", "H0STCNT0"); // 주식체결가
        input.put("tr_key", symbol);
        body.put("input", input);

        req.put("header", header);
        req.put("body", body);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(req)));
    }
}
