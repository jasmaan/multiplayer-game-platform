package ca.ucalgary.seng300.client;

import ca.ucalgary.seng300.server.dto.OutboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton WebSocket client with automatic reconnection.
 *
 * Reconnect back-off: 1 s → 2 s → 4 s → … capped at 30 s.
 * All inbound frames are dispatched to a registered {@link ClientEventDispatcher}.
 *
 * Usage:
 *   ServerConnection conn = ServerConnection.getInstance();
 *   conn.connect("ws://localhost:8080", token, dispatcher);
 *   conn.send(jsonFrame);
 */
public class ServerConnection {

    private static final Logger LOG = Logger.getLogger(ServerConnection.class.getName());

    private static final int MAX_BACKOFF_S = 30;

    private static volatile ServerConnection instance;

    private final ObjectMapper              mapper;
    private final ScheduledExecutorService  reconnectScheduler;

    private WebSocketClient         client;
    private ClientEventDispatcher   dispatcher;
    private String                  serverUri;
    private String                  authToken;
    private int                     backoffSeconds = 1;
    private volatile boolean        intentionalClose = false;

    // ── Singleton ──────────────────────────────────────────────────────────────

    private ServerConnection() {
        this.mapper = new ObjectMapper();
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    public static ServerConnection getInstance() {
        if (instance == null) {
            synchronized (ServerConnection.class) {
                if (instance == null) instance = new ServerConnection();
            }
        }
        return instance;
    }

    // ── Connection management ──────────────────────────────────────────────────

    /**
     * Establish the WebSocket connection and send AUTH_PING once open.
     * Blocks until the connection is open (or throws if it fails within 5 s).
     *
     * @param uri        WebSocket server URI (e.g. "ws://localhost:8080")
     * @param token      HMAC auth token obtained from POST /auth/login
     * @param dispatcher handler for all inbound server frames
     */
    public synchronized void connect(String uri, String token, ClientEventDispatcher dispatcher) {
        this.serverUri   = uri;
        this.authToken   = token;
        this.dispatcher  = dispatcher;
        this.intentionalClose = false;
        this.backoffSeconds   = 1;
        openSocket();
    }

    /** Gracefully close the connection without triggering a reconnect. */
    public synchronized void disconnect() {
        intentionalClose = true;
        if (client != null) client.close();
    }

    /** Send a raw JSON frame to the server. No-op if not connected. */
    public void send(String json) {
        if (client != null && client.isOpen()) {
            client.send(json);
        } else {
            LOG.warning("send() called while not connected — frame dropped");
        }
    }

    /** Convenience: serialise an object to JSON and send it. */
    public void send(Object payload) {
        try {
            send(mapper.writeValueAsString(payload));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to serialise frame", e);
        }
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    // ── Internal socket plumbing ───────────────────────────────────────────────

    private void openSocket() {
        try {
            URI uri = URI.create(serverUri);
            client = new WebSocketClient(uri) {

                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("WebSocket connected to " + serverUri);
                    backoffSeconds = 1; // reset back-off on successful connect
                    // Authenticate immediately
                    sendAuthPing();
                }

                @Override
                public void onMessage(String message) {
                    handleInbound(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LOG.info("WebSocket closed: code=" + code + " reason=" + reason);
                    if (!intentionalClose) scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    LOG.log(Level.WARNING, "WebSocket error", ex);
                }
            };
            client.connectBlocking(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not open WebSocket", e);
            if (!intentionalClose) scheduleReconnect();
        }
    }

    private void sendAuthPing() {
        try {
            String frame = mapper.writeValueAsString(
                java.util.Map.of("type", "AUTH_PING", "token", authToken));
            client.send(frame);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to send AUTH_PING", e);
        }
    }

    private void handleInbound(String rawJson) {
        if (dispatcher == null) return;
        try {
            OutboundMessage msg = mapper.readValue(rawJson, OutboundMessage.class);
            dispatcher.dispatch(msg);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse inbound frame: " + rawJson, e);
        }
    }

    private void scheduleReconnect() {
        int delay = backoffSeconds;
        backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF_S);
        LOG.info("Reconnecting in " + delay + " s…");
        reconnectScheduler.schedule(() -> {
            synchronized (ServerConnection.this) {
                if (!intentionalClose) openSocket();
            }
        }, delay, TimeUnit.SECONDS);
    }
}
