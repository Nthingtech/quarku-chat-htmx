package org.htmx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocketClient;
import jakarta.inject.Inject;

@WebSocketClient(path = "/v1/feed")
public class AdsbMilitaryClient {

    @Inject
    RadarWebSocket radarServer;

    @Inject
    ObjectMapper mapper;

    @OnTextMessage
    void onMessage(String msg) {
        try {
            JsonNode root = mapper.readTree(msg);
            JsonNode aircrafts = root.get("ac");

            if (aircrafts != null && aircrafts.isArray()) {
                for (JsonNode ac : aircrafts) {
                    if (isUsMilitary(ac)) {
                        double lat = ac.path("lat").asDouble();
                        double lon = ac.path("lon").asDouble();

                        if (isLatinAmerica(lat, lon)) {
                            // Envia para o seu servidor WebSocket (HTMX)
                            radarServer.notificarAlvo(ac);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // No Quarkus Dev mode, logs de erro ajudam muito
            System.err.println("Erro no parsing: " + e.getMessage());
        }
    }

    private boolean isUsMilitary(JsonNode ac) {
        String op = ac.path("ownOp").asText("").toLowerCase();
        return ac.path("mil").asInt() == 1 &&
                (op.contains("united states") || op.contains("us air force") || op.contains("us navy"));
    }

    private boolean isLatinAmerica(double lat, double lon) {
        return (lat <= 32.0 && lat >= -56.0) && (lon <= -35.0 && lon >= -118.0);
    }
}