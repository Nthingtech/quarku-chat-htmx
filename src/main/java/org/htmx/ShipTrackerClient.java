package org.htmx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocketClient;
import io.quarkus.websockets.next.WebSocketClientConnection;
import jakarta.inject.Inject;

@WebSocketClient(path = "/v0/stream", clientId = "ship-tracker")
public class ShipTrackerClient {

    @Inject
    ShipRadarServer radarServer;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String API_KEY = "fce25a73a1db953ab3b4e627aa7cba7c7a1fe399";

    @OnOpen
    void onOpen(WebSocketClientConnection connection) {
        System.out.println("🌐 Conectado ao servidor da AISStream. Enviando subscrição...");

        try {
            ObjectNode subscription = mapper.createObjectNode();
            subscription.put("APIKey", API_KEY);

            ArrayNode boundingBoxes = mapper.createArrayNode();

            // 🔥 ÁREA FOCADA: Europa + Mediterrâneo (MUITO TRÁFEGO!)
            // Esta área tem navios CONSTANTEMENTE
            ArrayNode europeArea = mapper.createArrayNode();
            europeArea.add(mapper.createArrayNode().add(35.0).add(-15.0));  // Sudoeste
            europeArea.add(mapper.createArrayNode().add(65.0).add(40.0));   // Nordeste

            boundingBoxes.add(europeArea);
            subscription.set("BoundingBoxes", boundingBoxes);
            subscription.set("FilterMessageTypes", mapper.createArrayNode().add("PositionReport"));

            connection.sendTextAndAwait(subscription.toString());
            System.out.println("✅ Subscrição enviada com sucesso!");
            System.out.println("🗺️ Monitorando: Europa, Mediterrâneo, Atlântico Norte");
            System.out.println("⏳ Aguardando dados de navios...");

        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar subscrição: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @OnTextMessage
    void onMessage(String msg) {
        // LOG: Mostra que está recebendo mensagens
        System.out.println("📡 Mensagem recebida (primeiros 150 chars): " + msg.substring(0, Math.min(150, msg.length())) + "...");

        try {
            JsonNode root = mapper.readTree(msg);
            String messageType = root.path("MessageType").asText();

            System.out.println("📋 Tipo da mensagem: " + messageType);

            if ("PositionReport".equals(messageType)) {
                JsonNode report = root.path("Message").path("PositionReport");
                JsonNode meta = root.path("MetaData");

                String name = meta.path("ShipName").asText("DESCONHECIDO").trim();
                String mmsi = String.valueOf(report.path("UserID").asInt());
                double lat = report.path("Latitude").asDouble();
                double lon = report.path("Longitude").asDouble();
                String flag = meta.path("Flag").asText("🏳️");

                System.out.println("🚢 NAVIO DETECTADO: " + name + " | MMSI: " + mmsi + " | Pos: " + lat + ", " + lon);

                radarServer.broadcastAlert(name, mmsi, lat, lon, flag);
            } else {
                System.out.println("⚠️ Mensagem ignorada (não é PositionReport)");
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao processar mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }
}