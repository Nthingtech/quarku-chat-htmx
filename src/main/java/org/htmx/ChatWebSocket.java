package org.htmx;

import io.quarkus.qute.Template;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@WebSocket(path = "/chat-ws")
public class ChatWebSocket {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();

    // Estatísticas de navios para o painel lateral
    private final AtomicInteger totalShips = new AtomicInteger(0);
    private final AtomicInteger cargoShips = new AtomicInteger(0);
    private final AtomicInteger tankerShips = new AtomicInteger(0);

    @Inject Template message;
    @Inject Template systemmsg;
    @Inject Template chatInput;
    @Inject ShipRadarServer radarServer;  // Para acessar navios via comandos

    public static class ChatMessage {
        public String type;
        public String value;
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        connections.put(connection.id(), connection);
        sendStats(connection);
    }

    @OnTextMessage
    public void onMessage(ChatMessage data, WebSocketConnection connection) {
        if ("LOGIN".equals(data.type)) {
            String username = data.value;
            sessions.put(connection.id(), username);
            broadcast(systemmsg.data("message", "🟢 " + username + " entrou na sala").render());
            String inputHtml = chatInput.data("username", username).render();
            connection.sendTextAndAwait(inputHtml);
        }
        else if ("MSG".equals(data.type)) {
            String username = sessions.get(connection.id());
            if (username != null && data.value != null && !data.value.isEmpty()) {
                if (data.value.startsWith("/")) {
                    handleCommand(data.value, connection, username);
                } else {
                    String html = message
                            .data("username", username)
                            .data("content", data.value)
                            .render();
                    broadcast(html);
                }
            }
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        String username = sessions.get(connection.id());
        if (username != null) {
            broadcast(systemmsg.data("message", "🔴 " + username + " saiu").render());
        }
        sessions.remove(connection.id());
        connections.remove(connection.id());
    }

    private void broadcast(String html) {
        connections.values().forEach(c -> {
            if (c.isOpen()) c.sendTextAndAwait(html);
        });
    }

    // 🔥 NOVO: Apenas atualiza estatísticas (SEM alerta)
    public void updateShipStats(String shipType) {
        totalShips.incrementAndGet();
        if (shipType.contains("Carga")) cargoShips.incrementAndGet();
        if (shipType.contains("Petroleiro") || shipType.contains("Tanque")) tankerShips.incrementAndGet();
        broadcastStats();
    }

    private void handleCommand(String command, WebSocketConnection connection, String username) {
        String[] parts = command.toLowerCase().split(" ");
        String cmd = parts[0];

        switch (cmd) {
            case "/navios" -> {
                if (parts.length > 1) {
                    handleNaviosFilter(parts[1], connection);
                } else {
                    handleNaviosCommand(connection);
                }
            }
            case "/radar" -> {
                String html = systemmsg
                        .data("message", "🔗 Acesse o radar: <a href='/naval-radar' style='color: #60a5fa;'>http://localhost:8080/naval-radar</a>")
                        .render();
                connection.sendTextAndAwait(html);
            }
            case "/ajuda", "/help" -> {
                String helpMsg = """
                    📋 Comandos disponíveis:
                    /navios - Últimos 5 navios detectados
                    /navios carga - Navios de carga recentes
                    /navios santos - Navios perto de Santos
                    /radar - Link para o radar naval
                    /ajuda - Mostra esta mensagem
                    """;
                String html = systemmsg.data("message", helpMsg).render();
                connection.sendTextAndAwait(html);
            }
            default -> {
                String html = systemmsg
                        .data("message", "❌ Comando desconhecido: " + cmd + ". Digite /ajuda para ver comandos disponíveis.")
                        .render();
                connection.sendTextAndAwait(html);
            }
        }
    }

    private void handleNaviosCommand(WebSocketConnection connection) {
        var ships = radarServer.getRecentShips();

        if (ships.isEmpty()) {
            String html = systemmsg
                    .data("message", "📭 Nenhum navio detectado ainda.")
                    .render();
            connection.sendTextAndAwait(html);
            return;
        }

        StringBuilder msg = new StringBuilder("🚢 Últimos navios detectados:\n\n");
        for (int i = 0; i < Math.min(5, ships.size()); i++) {
            var ship = ships.get(i);
            msg.append(String.format("%d. %s %s\n   📍 %s | MMSI: %s\n   ⚡ %.0f km/h %s\n",
                    i + 1, ship.shipType, ship.name, ship.location, ship.mmsi, ship.speedKmh, ship.direction));
        }

        String html = systemmsg.data("message", msg.toString()).render();
        connection.sendTextAndAwait(html);
    }

    private void handleNaviosFilter(String filter, WebSocketConnection connection) {
        var ships = radarServer.getRecentShips();

        var filtered = ships.stream()
                .filter(ship -> ship.shipType.toLowerCase().contains(filter)
                        || ship.location.toLowerCase().contains(filter)
                        || ship.name.toLowerCase().contains(filter))
                .limit(5)
                .toList();

        if (filtered.isEmpty()) {
            String html = systemmsg
                    .data("message", "📭 Nenhum navio encontrado para: " + filter)
                    .render();
            connection.sendTextAndAwait(html);
            return;
        }

        StringBuilder msg = new StringBuilder("🚢 Navios filtrados por '" + filter + "':\n\n");
        for (int i = 0; i < filtered.size(); i++) {
            var ship = filtered.get(i);
            msg.append(String.format("%d. %s %s\n   📍 %s | MMSI: %s\n",
                    i + 1, ship.shipType, ship.name, ship.location, ship.mmsi));
        }

        String html = systemmsg.data("message", msg.toString()).render();
        connection.sendTextAndAwait(html);
    }

    private void sendStats(WebSocketConnection connection) {
        String statsHtml = """
            <div id="ship-stats" hx-swap-oob="innerHTML">
                <div class="stat-item">
                    <span class="stat-value">%d</span>
                    <span class="stat-label">Total</span>
                </div>
                <div class="stat-item">
                    <span class="stat-value">%d</span>
                    <span class="stat-label">Carga</span>
                </div>
                <div class="stat-item">
                    <span class="stat-value">%d</span>
                    <span class="stat-label">Petroleiros</span>
                </div>
            </div>
            """.formatted(totalShips.get(), cargoShips.get(), tankerShips.get());

        connection.sendTextAndAwait(statsHtml);
    }

    private void broadcastStats() {
        String statsHtml = """
            <div id="ship-stats" hx-swap-oob="innerHTML">
                <div class="stat-item">
                    <span class="stat-value">%d</span>
                    <span class="stat-label">Total</span>
                </div>
                <div class="stat-item">
                    <span class="stat-value">%d</span>
                    <span class="stat-label">Carga</span>
                </div>
                <div class="stat-item">
                    <span class="stat-value">%d</span>
                    <span class="stat-label">Petroleiros</span>
                </div>
            </div>
            """.formatted(totalShips.get(), cargoShips.get(), tankerShips.get());

        broadcast(statsHtml);
    }
}