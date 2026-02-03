package org.htmx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocketClient;
import io.quarkus.websockets.next.WebSocketClientConnection;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;


@WebSocketClient(path = "/v0/stream", clientId = "ship-tracker")
public class ShipTrackerClient {

    @Inject
    ShipRadarServer radarServer;

    private final ObjectMapper mapper = new ObjectMapper();

    // 🔑 COLOQUE SUA API KEY AQUI
    private final String API_KEY = "dc826dd6f62d3dced787c75fc6e0d6894f7ec59d";

    @OnOpen
    void onOpen(WebSocketClientConnection connection) {
        System.out.println("🌐 Conectado ao servidor da AISStream. Enviando subscrição...");

        try {
            ObjectNode subscription = mapper.createObjectNode();
            subscription.put("APIKey", API_KEY);

            ArrayNode boundingBoxes = mapper.createArrayNode();

            // 🇧🇷 ÁREA DO BRASIL - Costa brasileira completa
            // Do Rio Grande do Sul até o Amapá
            ArrayNode brazilArea = mapper.createArrayNode();
            brazilArea.add(mapper.createArrayNode().add(-35.0).add(-55.0));  // Sudoeste (RS)
            brazilArea.add(mapper.createArrayNode().add(5.0).add(-30.0));     // Nordeste (AP)

            boundingBoxes.add(brazilArea);
            subscription.set("BoundingBoxes", boundingBoxes);
            subscription.set("FilterMessageTypes", mapper.createArrayNode().add("PositionReport"));

            connection.sendTextAndAwait(subscription.toString());
            System.out.println("✅ Subscrição enviada com sucesso!");
            System.out.println("🇧🇷 Monitorando: Costa Brasileira (RS até AP)");
            System.out.println("📍 Área: Lat -35° a 5° | Lon -55° a -30°");
            System.out.println("⏳ Aguardando dados de navios...");

        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar subscrição: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @OnBinaryMessage
    void onBinaryMessage(Buffer buffer) {
        String msg = buffer.toString(StandardCharsets.UTF_8);

        try {
            JsonNode root = mapper.readTree(msg);
            String messageType = root.path("MessageType").asText();

            if ("PositionReport".equals(messageType)) {
                JsonNode report = root.path("Message").path("PositionReport");
                JsonNode meta = root.path("MetaData");

                // Dados básicos
                String name = meta.path("ShipName").asText("DESCONHECIDO").trim();
                if (name.isEmpty()) name = "DESCONHECIDO";

                String mmsi = String.valueOf(report.path("UserID").asInt());
                double lat = report.path("Latitude").asDouble();
                double lon = report.path("Longitude").asDouble();
                String flag = meta.path("Flag").asText("🏳️");

                // 🔥 DADOS ADICIONAIS ÚTEIS
                double speedKnots = report.path("Sog").asDouble(); // Speed Over Ground
                double speedKmh = speedKnots * 1.852; // Converte para km/h

                int heading = report.path("TrueHeading").asInt(); // Direção
                String direction = getDirection(heading);

                // Tipo de navio (se disponível)
                String shipType = getShipType(meta.path("ShipType").asInt());

                // Destino (se disponível)
                String destination = meta.path("Destination").asText("").trim();
                if (destination.isEmpty()) destination = "Não informado";

                System.out.println("\n🚢 ========================================");
                System.out.println("🚢 NAVIO DETECTADO NA COSTA BRASILEIRA!");
                System.out.println("🚢 Nome: " + name);
                System.out.println("🚢 Tipo: " + shipType);
                System.out.println("🚢 Bandeira: " + flag);
                System.out.println("🚢 MMSI: " + mmsi);
                System.out.println("📍 Posição: " + String.format("%.4f, %.4f", lat, lon));
                System.out.println("🎯 Destino: " + destination);
                System.out.println("⚡ Velocidade: " + String.format("%.1f", speedKmh) + " km/h");
                System.out.println("🧭 Direção: " + direction);
                System.out.println("🚢 ========================================\n");

                radarServer.broadcastAlert(name, mmsi, lat, lon, flag, shipType, destination, speedKmh, direction);
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao processar mensagem: " + e.getMessage());
        }
    }

    @OnTextMessage
    void onTextMessage(String msg) {
        System.out.println("📝 Mensagem de texto recebida: " + msg);
    }

    // Converte código de tipo de navio para descrição
    private String getShipType(int code) {
        return switch (code) {
            case 30, 31, 32, 33, 34, 35, 36, 37, 38, 39 -> "🎣 Pesca";
            case 40, 41, 42, 43, 44, 45, 46, 47, 48, 49 -> "⛴️ Alta velocidade";
            case 50 -> "🚤 Piloto";
            case 51 -> "🔍 Busca e resgate";
            case 52 -> "⛵ Rebocador";
            case 53 -> "🚢 Porta-contentores";
            case 54 -> "🛢️ Petroleiro";
            case 55 -> "⚓ Militar";
            case 60, 61, 62, 63, 64, 65, 66, 67, 68, 69 -> "🛳️ Passageiros";
            case 70, 71, 72, 73, 74, 75, 76, 77, 78, 79 -> "📦 Carga";
            case 80, 81, 82, 83, 84, 85, 86, 87, 88, 89 -> "🛢️ Tanque";
            default -> "🚢 Navio";
        };
    }

    // Converte heading (0-359) para direção cardeal
    private String getDirection(int heading) {
        if (heading == 511) return "Não disponível";
        if (heading < 0 || heading > 359) return "Desconhecido";

        String[] directions = {"Norte", "Nordeste", "Leste", "Sudeste", "Sul", "Sudoeste", "Oeste", "Noroeste"};
        int index = (int) Math.round(((double) heading % 360) / 45) % 8;
        return directions[index];
    }
}