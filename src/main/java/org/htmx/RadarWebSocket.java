package org.htmx;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.qute.Template;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@WebSocket(path = "/radar-ws")
public class RadarWebSocket {

    // 1. REMOVIDO O @INJECT. Gerenciamos a lista manualmente.
    // Usamos CopyOnWriteArrayList para evitar erros de concorrência.
    private final List<WebSocketConnection> connections = new CopyOnWriteArrayList<>();

    @Inject
    Template alert;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        // 2. Adicionamos a conexão na lista quando o navegador entra
        connections.add(connection);
        System.out.println("🌐 Novo navegador conectado: " + connection.id());
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        // 3. Removemos da lista quando o navegador sai (evita memory leak)
        connections.remove(connection);
        System.out.println("❌ Navegador desconectado: " + connection.id());
    }

    public void notificarAlvo(JsonNode ac) {
        String html = alert
                .data("flight", ac.path("flight").asText("N/A"))
                .data("type", ac.path("t").asText("N/A"))
                .data("lat", ac.path("lat").asDouble())
                .data("lon", ac.path("lon").asDouble())
                .render();

        // 4. Agora iteramos sobre nossa lista manual
        for (WebSocketConnection connection : connections) {
            // Verifica se ainda está aberta antes de enviar
            if (connection != null) { // A verificação isOpen() é implícita no sendText, mas o objeto deve existir
                connection.sendTextAndAwait(html);
            }
        }
    }
}
