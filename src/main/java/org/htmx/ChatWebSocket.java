package org.htmx;

import io.quarkus.qute.Template;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/chat-ws")
public class ChatWebSocket {

    // Mapa para guardar: ID da Conexão -> Nome do Usuário
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    // Mapa para guardar as conexões ativas para fazer o broadcast
    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();

    @Inject Template message;    // message.html
    @Inject Template systemmsg;  // systemmsg.html
    @Inject Template chatInput;  // NOVO: Template para a área de input do chat

    // DTO atualizado para saber o tipo da mensagem
    public static class ChatMessage {
        public String type;    // "LOGIN" ou "MSG"
        public String value;   // O conteúdo (nome do usuário ou texto da mensagem)
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        connections.put(connection.id(), connection);
        // Não anunciamos nada ainda, pois o usuário não tem nome
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
                String html = message
                        .data("username", username)
                        .data("content", data.value)
                        .render();
                broadcast(html);
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
}