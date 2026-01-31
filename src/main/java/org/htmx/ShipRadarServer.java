package org.htmx;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@WebSocket(path = "/ships-ws")
public class ShipRadarServer {

    private static final List<WebSocketConnection> connections = new CopyOnWriteArrayList<>();

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        connections.add(connection);
        System.out.println("✅ Novo terminal de radar conectado: " + connection.id());
        System.out.println("📊 Total de terminais conectados: " + connections.size());

        // 🔥 ENVIA MENSAGEM DE CONEXÃO ESTABELECIDA
        String welcomeHtml = """
            <div id="radar-log" hx-swap-oob="innerHTML">
                <div class="alert-entry" style="color: #00ff00; font-style: italic;">
                    ✅ Conexão estabelecida com sucesso! Monitorando oceanos globalmente...
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

    public void broadcastAlert(String name, String mmsi, double lat, double lon, String flag) {
        System.out.println("📢 BROADCASTING navio '" + name + "' para " + connections.size() + " terminais");

        if (connections.isEmpty()) {
            System.err.println("⚠️ ATENÇÃO: Nenhum terminal conectado! Abra http://localhost:8080/naval-radar no navegador!");
            return;
        }

        String html = """
            <div id="radar-log" hx-swap-oob="afterbegin">
                <div class="alert-entry">
                    <span class="blink">[DETECTADO]</span> 
                    %s <strong>%s</strong> (MMSI: %s) | Pos: %.4f, %.4f
                </div>
            </div>
            """.formatted(flag, name, mmsi, lat, lon);

        int sent = 0;
        for (WebSocketConnection conn : connections) {
            if (conn.isOpen()) {
                conn.sendTextAndAwait(html);
                sent++;
            }
        }

        System.out.println("✅ HTML enviado para " + sent + " terminal(is)");
    }
}