package ca.ucalgary.seng300.server;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.server.dto.OutboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded WebSocket server (org.java-websocket 1.5.7).
 *
 * Lifecycle:
 *   onOpen    → no-op; players must authenticate via AUTH_PING before any other frame
 *   onMessage → delegate to MessageRouter
 *   onClose   → unregister from SessionRegistry; revoke auth token
 *   onError   → log and close
 *
 * A periodic heartbeat thread pings all open connections every 30 s so that
 * idle sockets are detected and closed by the OS TCP stack.
 */
public class GameWebSocketServer extends WebSocketServer {

    private static final Logger LOG = Logger.getLogger(GameWebSocketServer.class.getName());

    private static final int HEARTBEAT_INTERVAL_S = 30;

    private final MessageRouter           router;
    private final SessionRegistry         registry;
    private final AuthService             auth;
    private final PlatformCore            platform;
    private final ObjectMapper            mapper;
    private final ScheduledExecutorService heartbeat;

    public GameWebSocketServer(int port,
                               MessageRouter router,
                               SessionRegistry registry,
                               AuthService auth,
                               PlatformCore platform) {
        super(new InetSocketAddress(port));
        this.router    = router;
        this.registry  = registry;
        this.auth      = auth;
        this.platform  = platform;
        this.mapper    = new ObjectMapper();
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-heartbeat");
            t.setDaemon(true);
            return t;
        });
        setReuseAddr(true);
    }

    // ── WebSocketServer callbacks ──────────────────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOG.info("WS connection opened: " + conn.getRemoteSocketAddress());
        // No registration yet — wait for AUTH_PING
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        router.route(conn, message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String playerId = registry.getPlayerId(conn);
        if (playerId != null) {
            LOG.info("Player disconnected: " + playerId + " (code=" + code + ")");
            platform.logout(playerId);
        } else {
            LOG.info("Unauthenticated connection closed: " + conn.getRemoteSocketAddress());
        }
        registry.unregister(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOG.log(Level.WARNING, "WebSocket error on " +
            (conn != null ? conn.getRemoteSocketAddress() : "null"), ex);
        if (conn != null) {
            registry.unregister(conn);
            conn.close();
        }
    }

    @Override
    public void onStart() {
        LOG.info("WebSocket server started on port " + getPort());
        scheduleHeartbeat();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Gracefully stop the heartbeat scheduler before shutting down. */
    public void shutdown() {
        heartbeat.shutdown();
        try {
            stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Heartbeat ──────────────────────────────────────────────────────────────

    /**
     * Sends a PING frame to every open connection every 30 s.
     * Connections that fail to pong are closed by the OS; onClose fires
     * and the registry is cleaned up automatically.
     */
    private void scheduleHeartbeat() {
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                OutboundMessage ping = OutboundMessage.of("PING", null);
                String json = mapper.writeValueAsString(ping);
                for (WebSocket conn : getConnections()) {
                    if (conn.isOpen()) {
                        conn.send(json);
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Heartbeat error", e);
            }
        }, HEARTBEAT_INTERVAL_S, HEARTBEAT_INTERVAL_S, TimeUnit.SECONDS);
    }
}
