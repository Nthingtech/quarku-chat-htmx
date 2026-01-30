package org.htmx;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.websockets.next.WebSocketConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class RadarStartup {

    @Inject
    WebSocketConnector<AdsbMilitaryClient> connector;

    void onStart(@Observes StartupEvent ev) {
        connector.baseUri("wss://api.theairtraffic.com/v1/feed") // Verifique o path correto da API
                .connect()
                .subscribe().with(
                        client -> System.out.println("Conectado ao Radar com sucesso!"),
                        failure -> System.err.println("Falha ao conectar no Radar: " + failure.getMessage())
                );
    }
}

