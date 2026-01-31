package org.htmx;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.websockets.next.WebSocketConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;

@ApplicationScoped
public class AppLifecycle {

    @Inject
    WebSocketConnector<ShipTrackerClient> connector;

    void onStart(@Observes StartupEvent ev) {
        System.out.println("🚀 Iniciando sistema de rastreamento naval...");

        try {
            // 🔥 IMPORTANTE: baseUri() deve ser chamado ANTES de connect()
            connector
                    .baseUri(URI.create("wss://stream.aisstream.io"))
                    .connectAndAwait();

            System.out.println("✅ Conexão WebSocket com AISStream iniciada!");
        } catch (Exception e) {
            System.err.println("❌ Erro ao conectar com AISStream: " + e.getMessage());
            e.printStackTrace();
        }
    }
}