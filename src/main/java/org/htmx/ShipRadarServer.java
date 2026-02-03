package org.htmx;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@WebSocket(path = "/ships-ws")
public class ShipRadarServer {

    private static final List<WebSocketConnection> connections = new CopyOnWriteArrayList<>();
    private static final AtomicInteger shipCount = new AtomicInteger(0);

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        connections.add(connection);
        System.out.println("✅ Novo terminal de radar conectado: " + connection.id());
        System.out.println("📊 Total de terminais conectados: " + connections.size());

        // Envia mensagem de boas-vindas
        String welcomeHtml = """
            <div id="radar-log" hx-swap-oob="innerHTML">
                <div class="alert-entry" style="color: #00ff00; font-style: italic;">
                    ✅ Conexão estabelecida! Monitorando costa brasileira...
                </div>
            </div>
            """;
        connection.sendTextAndAwait(welcomeHtml);
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        connections.remove(connection);
        System.out.println("❌ Terminal desconectado: " + connection.id());
        System.out.println("📊 Total de terminais conectados: " + connections.size());
    }

    public void broadcastAlert(String name, String mmsi, double lat, double lon, String flag,
                               String shipType, String destination, double speedKmh, String direction) {

        int currentCount = shipCount.incrementAndGet();
        System.out.println("📢 BROADCASTING navio '" + name + "' para " + connections.size() + " terminais");
        System.out.println("📊 Total de navios detectados: " + currentCount);

        if (connections.isEmpty()) {
            System.err.println("⚠️ ATENÇÃO: Nenhum terminal conectado!");
            return;
        }

        // Determina localização aproximada no Brasil
        String location = getLocationInBrazil(lat, lon);

        // HTML melhorado com mais informações úteis
        String html = """
            <div id="radar-log" hx-swap-oob="afterbegin">
                <div class="alert-entry">
                    <div style="display: flex; justify-content: space-between; align-items: start;">
                        <div style="flex: 1;">
                            <span class="blink">[DETECTADO]</span> 
                            %s <strong>%s</strong> %s
                            <br>
                            <small style="color: #5588aa;">
                                📍 %s | MMSI: %s
                                <br>
                                ⚡ %.0f km/h %s | 🎯 %s
                            </small>
                        </div>
                    </div>
                </div>
            </div>
            <div id="ship-count" hx-swap-oob="innerHTML">%d</div>
            """.formatted(
                flag,
                name,
                shipType,
                location,
                mmsi,
                speedKmh,
                direction,
                destination,
                currentCount
        );

        int sent = 0;
        for (WebSocketConnection conn : connections) {
            if (conn.isOpen()) {
                conn.sendTextAndAwait(html);
                sent++;
            }
        }

        System.out.println("✅ HTML enviado para " + sent + " terminal(is)");
    }

    // Identifica região aproximada no Brasil baseado em coordenadas
    private String getLocationInBrazil(double lat, double lon) {
        // Rio Grande do Sul
        if (lat < -27) return "Próximo ao Rio Grande do Sul";
        // Santa Catarina
        if (lat < -25) return "Próximo a Santa Catarina";
        // Paraná
        if (lat < -24) return "Próximo ao Paraná";
        // São Paulo
        if (lat < -22) return "Próximo a São Paulo";
        // Rio de Janeiro
        if (lat < -20) return "Próximo ao Rio de Janeiro";
        // Espírito Santo
        if (lat < -18) return "Próximo ao Espírito Santo";
        // Bahia
        if (lat < -12) return "Próximo à Bahia";
        // Sergipe/Alagoas
        if (lat < -9) return "Próximo a Sergipe/Alagoas";
        // Pernambuco
        if (lat < -7) return "Próximo a Pernambuco";
        // Paraíba/Rio Grande do Norte
        if (lat < -5) return "Próximo a Paraíba/RN";
        // Ceará
        if (lat < -3) return "Próximo ao Ceará";
        // Piauí/Maranhão
        if (lat < 0) return "Próximo ao Maranhão";
        // Pará/Amapá
        return "Próximo ao Pará/Amapá";
    }
}