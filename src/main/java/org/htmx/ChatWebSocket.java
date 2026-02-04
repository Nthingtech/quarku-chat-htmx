package org.htmx;

import io.quarkus.qute.Location;
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
import java.util.List;
import java.util.ArrayList;

@ApplicationScoped
@WebSocket(path = "/chat-ws")
public class ChatWebSocket {

    // Mapa para guardar: ID da Conexão -> Nome do Usuário
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    // Mapa para guardar as conexões ativas para fazer o broadcast
    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();

    // 🔥 NOVO: Estatísticas de navios para o painel lateral
    private final AtomicInteger totalShips = new AtomicInteger(0);
    private final AtomicInteger cargoShips = new AtomicInteger(0);
    private final AtomicInteger tankerShips = new AtomicInteger(0);
    private final List<ShipInfo> recentShips = new ArrayList<>();

    @Inject Template message;    // message.html
    @Inject Template systemmsg;  // systemmsg.html
    @Inject Template chatInput;  // chatInput.html
    @Inject
    @Location("ship-alert.html")
    Template shipAlert;  // 🔥 NOVO: ship-alert-chat.html

    // DTO para informações de navio
    public static class ShipInfo {
        public String name;
        public String type;
        public String location;
        public String time;

        public ShipInfo(String name, String type, String location) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = java.time.LocalTime.now().toString().substring(0, 5);
        }
    }

    // DTO atualizado para saber o tipo da mensagem
    public static class ChatMessage {
        public String type;    // "LOGIN", "MSG", ou "COMMAND"
        public String value;   // O conteúdo (nome do usuário, texto da mensagem, ou comando)
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        connections.put(connection.id(), connection);

        // 🔥 NOVO: Envia estatísticas iniciais
        sendStats(connection);
    }

    @OnTextMessage
    public void onMessage(ChatMessage data, WebSocketConnection connection) {

        // CENÁRIO 1: Usuário tentando entrar (Login)
        if ("LOGIN".equals(data.type)) {
            String username = data.value;
            sessions.put(connection.id(), username);

            // 1. Avisa a todos que entrou
            broadcast(systemmsg.data("message", "🟢 " + username + " entrou na sala").render());

            // 2. Envia APENAS para quem entrou o formulário de chat (Troca de tela)
            String inputHtml = chatInput.data("username", username).render();
            connection.sendTextAndAwait(inputHtml);
        }

        // CENÁRIO 2: Usuário enviando mensagem
        else if ("MSG".equals(data.type)) {
            String username = sessions.get(connection.id());

            if (username != null && data.value != null && !data.value.isEmpty()) {

                // 🔥 NOVO: Verifica se é um comando
                if (data.value.startsWith("/")) {
                    handleCommand(data.value, connection, username);
                } else {
                    // Mensagem normal
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

    // 🔥 NOVO: Método público para receber alertas de navios
    public void broadcastShipAlert(String name, String type, String location, String flag,
                                   double speed, String destination) {
        System.out.println("📢 [CHAT] Enviando alerta de navio: " + name);

        // Atualiza estatísticas
        totalShips.incrementAndGet();
        if (type.contains("Carga")) cargoShips.incrementAndGet();
        if (type.contains("Petroleiro") || type.contains("Tanque")) tankerShips.incrementAndGet();

        // Adiciona aos navios recentes (mantém só os últimos 5)
        synchronized (recentShips) {
            recentShips.add(0, new ShipInfo(name, type, location));
            if (recentShips.size() > 5) {
                recentShips.remove(5);
            }
        }

        // Cria mensagem de alerta formatada
        String alertHtml = """
            <div id="msgs" hx-swap-oob="beforeend">
                <div class="msg ship-alert-msg">
                    <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                        <span style="font-size: 1.5em;">🚢</span>
                        <strong style="color: #0ea5e9;">RADAR NAVAL</strong>
                    </div>
                    <div style="font-size: 0.95em;">
                        %s <strong>%s</strong> %s<br>
                        📍 %s<br>
                        ⚡ %.0f km/h | 🎯 %s
                    </div>
                    <a href="/naval-radar" style="color: #60a5fa; text-decoration: underline; font-size: 0.9em; margin-top: 5px; display: inline-block;">
                        Ver no radar →
                    </a>
                </div>
            </div>
            """.formatted(flag, name, type, location, speed, destination);

        broadcast(alertHtml);

        // Atualiza painel lateral para todos
        broadcastStats();
    }

    // 🔥 NOVO: Trata comandos do chat
    private void handleCommand(String command, WebSocketConnection connection, String username) {
        String[] parts = command.toLowerCase().split(" ");
        String cmd = parts[0];

        switch (cmd) {
            case "/navios" -> {
                if (parts.length > 1) {
                    // /navios [filtro]
                    handleNaviosFilter(parts[1], connection);
                } else {
                    // /navios (sem filtro)
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

    // 🔥 NOVO: Comando /navios
    private void handleNaviosCommand(WebSocketConnection connection) {
        synchronized (recentShips) {
            if (recentShips.isEmpty()) {
                String html = systemmsg
                        .data("message", "📭 Nenhum navio detectado ainda.")
                        .render();
                connection.sendTextAndAwait(html);
                return;
            }

            StringBuilder msg = new StringBuilder("🚢 Últimos navios detectados:\n\n");
            for (int i = 0; i < Math.min(5, recentShips.size()); i++) {
                ShipInfo ship = recentShips.get(i);
                msg.append(String.format("%d. %s %s\n   📍 %s | ⏰ %s\n",
                        i + 1, ship.type, ship.name, ship.location, ship.time));
            }

            String html = systemmsg.data("message", msg.toString()).render();
            connection.sendTextAndAwait(html);
        }
    }

    // 🔥 NOVO: Comando /navios [filtro]
    private void handleNaviosFilter(String filter, WebSocketConnection connection) {
        synchronized (recentShips) {
            List<ShipInfo> filtered = recentShips.stream()
                    .filter(ship -> ship.type.toLowerCase().contains(filter)
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
                ShipInfo ship = filtered.get(i);
                msg.append(String.format("%d. %s %s\n   📍 %s | ⏰ %s\n",
                        i + 1, ship.type, ship.name, ship.location, ship.time));
            }

            String html = systemmsg.data("message", msg.toString()).render();
            connection.sendTextAndAwait(html);
        }
    }

    // 🔥 NOVO: Envia estatísticas para um cliente específico
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

    // 🔥 NOVO: Atualiza estatísticas para todos
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