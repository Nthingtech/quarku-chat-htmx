package org.htmx;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@WebSocket(path = "/ships-ws")
public class ShipRadarServer {

    private static final List<WebSocketConnection> connections = new CopyOnWriteArrayList<>();
    private static final AtomicInteger shipCount = new AtomicInteger(0);
    private static final List<ShipData> recentShips = new CopyOnWriteArrayList<>();
    private static final int MAX_SHIPS_CACHE = 50;

    public static class ShipData {
        public String name;
        public String mmsi;
        public double lat;
        public double lon;
        public String flag;
        public String shipType;
        public String destination;
        public double speedKmh;
        public String direction;
        public String location;
        public LocalDateTime timestamp;

        public ShipData(String name, String mmsi, double lat, double lon, String flag,
                        String shipType, String destination, double speedKmh,
                        String direction, String location) {
            this.name = name;
            this.mmsi = mmsi;
            this.lat = lat;
            this.lon = lon;
            this.flag = flag;
            this.shipType = shipType;
            this.destination = destination;
            this.speedKmh = speedKmh;
            this.direction = direction;
            this.location = location;
            this.timestamp = LocalDateTime.now();
        }

        public String toHtml(int index) {
            return """
                <div class="alert-entry" data-type="%s">
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
                """.formatted(shipType.toLowerCase(), flag, name, shipType, location, mmsi, speedKmh, direction, destination);
        }
    }

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        connections.add(connection);
        System.out.println("✅ Novo terminal conectado");
        sendCachedShips(connection);
        connection.sendTextAndAwait("<div id=\"ship-count\" hx-swap-oob=\"innerHTML\">" + shipCount.get() + "</div>");
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        connections.remove(connection);
    }

    public void broadcastAlert(String name, String mmsi, double lat, double lon, String flag,
                               String shipType, String destination, double speedKmh, String direction) {
        int currentCount = shipCount.incrementAndGet();
        String location = getLocationInBrazil(lat, lon);

        ShipData shipData = new ShipData(name, mmsi, lat, lon, flag, shipType, destination, speedKmh, direction, location);
        recentShips.add(0, shipData);
        if (recentShips.size() > MAX_SHIPS_CACHE) recentShips.remove(MAX_SHIPS_CACHE);

        if (connections.isEmpty()) return;

        String html = """
            <div id="radar-log" hx-swap-oob="afterbegin">%s</div>
            <div id="ship-count" hx-swap-oob="innerHTML">%d</div>
            """.formatted(shipData.toHtml(currentCount), currentCount);

        connections.forEach(c -> { if (c.isOpen()) c.sendTextAndAwait(html); });
    }

    private void sendCachedShips(WebSocketConnection connection) {
        if (recentShips.isEmpty()) {
            connection.sendTextAndAwait("""
                <div id="radar-log" hx-swap-oob="innerHTML">
                    <div class="alert-entry welcome-msg" style="color: #94a3b8; text-align: center;">
                        <span class="status-dot"></span>✅ Conectado! Monitorando...
                    </div>
                </div>
                """);
            return;
        }

        StringBuilder html = new StringBuilder();
        int limit = Math.min(20, recentShips.size());
        for (int i = 0; i < limit; i++) html.append(recentShips.get(i).toHtml(shipCount.get() - i));
        connection.sendTextAndAwait("<div id=\"radar-log\" hx-swap-oob=\"innerHTML\">" + html + "</div>");
    }

    private String getLocationInBrazil(double lat, double lon) {
        if (lat < -27) return "Próximo ao Rio Grande do Sul";
        if (lat < -25) return "Próximo a Santa Catarina";
        if (lat < -24) return "Próximo ao Paraná";
        if (lat < -22) return "Próximo a São Paulo";
        if (lat < -20) return "Próximo ao Rio de Janeiro";
        if (lat < -18) return "Próximo ao Espírito Santo";
        if (lat < -12) return "Próximo à Bahia";
        if (lat < -9) return "Próximo a Sergipe/Alagoas";
        if (lat < -7) return "Próximo a Pernambuco";
        if (lat < -5) return "Próximo a Paraíba/RN";
        if (lat < -3) return "Próximo ao Ceará";
        if (lat < 0) return "Próximo ao Maranhão";
        return "Próximo ao Pará/Amapá";
    }

    public int getTotalShips() { return shipCount.get(); }
    public List<ShipData> getRecentShips() { return List.copyOf(recentShips); }
}