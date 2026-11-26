package ca.ucalgary.seng300.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton network facade used by all UI controllers when running in networked mode.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>HTTP helpers for POST /auth/login and POST /auth/register</li>
 *   <li>WebSocket lifecycle via {@link ServerConnection}</li>
 *   <li>{@link ClientEventDispatcher} shared across all controllers</li>
 *   <li>Typed {@link #send(String, Map)} helper for outbound WebSocket frames</li>
 * </ul>
 *
 * <p>Usage flow:
 * <pre>
 *   NetworkClient nc = NetworkClient.getInstance();
 *   nc.setServerAddresses("ws://localhost:8080", "http://localhost:8081"); // optional override
 *   Map result = nc.login("username", "password");
 *   nc.connect((String) result.get("token"), (String) result.get("playerId"));
 *   nc.getDispatcher().on("MATCH_FOUND", payload -> …);
 *   nc.send("JOIN_QUEUE", Map.of("gameType", "TicTacToe"));
 * </pre>
 */
public class NetworkClient {

    private static final Logger LOG = Logger.getLogger(NetworkClient.class.getName());

    private static volatile NetworkClient instance;

    private final ObjectMapper mapper = new ObjectMapper();

    private String serverHttpBase = "http://localhost:8081";
    private String serverWsUri    = "ws://localhost:8080";

    private String                playerId;
    private String                token;
    private ClientEventDispatcher dispatcher;
    private boolean               networkMode = false;

    private NetworkClient() {}

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static NetworkClient getInstance() {
        if (instance == null) {
            synchronized (NetworkClient.class) {
                if (instance == null) instance = new NetworkClient();
            }
        }
        return instance;
    }

    // ── Configuration ──────────────────────────────────────────────────────────

    /**
     * Override the server addresses (call before login/connect).
     * Defaults: {@code ws://localhost:8080} and {@code http://localhost:8081}.
     */
    public void setServerAddresses(String wsUri, String httpBase) {
        this.serverWsUri  = wsUri;
        this.serverHttpBase = httpBase;
    }

    // ── REST helpers ───────────────────────────────────────────────────────────

    /**
     * POST /auth/login with {@code {username, password}}.
     * Returns the response body as a {@code Map<String,Object>} or {@code null} on error.
     */
    public Map<String, Object> login(String username, String password) {
        return postJson(serverHttpBase + "/auth/login",
                Map.of("username", username, "password", password));
    }

    /**
     * POST /auth/register with {@code {firstName, lastName, username, password}}.
     * Returns the response body or {@code null} on error.
     */
    public Map<String, Object> register(String firstName, String lastName,
                                        String username, String password) {
        return postJson(serverHttpBase + "/auth/register",
                Map.of("firstName", firstName, "lastName", lastName,
                       "username",  username,  "password", password));
    }

    // ── WebSocket connection ────────────────────────────────────────────────────

    /**
     * Connect to the WebSocket server using the given token.
     * Creates a fresh {@link ClientEventDispatcher} each time.
     *
     * @return {@code true} if connected successfully
     */
    public boolean connect(String token, String playerId) {
        this.token      = token;
        this.playerId   = playerId;
        this.dispatcher = new ClientEventDispatcher();
        try {
            ServerConnection.getInstance().connect(serverWsUri, token, dispatcher);
            networkMode = true;
            LOG.info("NetworkClient connected as " + playerId);
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not connect to server", e);
            networkMode = false;
            return false;
        }
    }

    /** Gracefully disconnect and set networkMode = false. */
    public void disconnect() {
        networkMode = false;
        ServerConnection.getInstance().disconnect();
    }

    /**
     * Returns {@code true} when connected AND the WebSocket is open.
     */
    public boolean isNetworkMode() {
        return networkMode && ServerConnection.getInstance().isConnected();
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public ClientEventDispatcher getDispatcher() { return dispatcher; }
    public String getPlayerId()   { return playerId; }
    public String getToken()      { return token; }

    // ── WebSocket send helpers ─────────────────────────────────────────────────

    /**
     * Send a typed frame with no payload.
     */
    public void send(String type) {
        send(type, null);
    }

    /**
     * Build and send a WebSocket frame: {@code { "type": ..., "payload": ... }}.
     * No-op if not connected.
     */
    public void send(String type, Map<String, Object> payload) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("type", type);
        if (payload != null) frame.put("payload", payload);
        ServerConnection.getInstance().send(frame);
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    /**
     * HTTP GET → returns the response as a parsed List, or an empty list on error.
     */
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> getJsonList(String path) {
        String url = serverHttpBase + path;
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return mapper.readValue(resp.body(), java.util.List.class);
            }
        } catch (java.net.ConnectException ce) {
            LOG.fine("Server not reachable: " + url);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "GET failed: " + url, e);
        }
        return java.util.List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(String url, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return mapper.readValue(resp.body(), Map.class);
            }
            LOG.warning("HTTP " + resp.statusCode() + " from " + url + ": " + resp.body());
        } catch (java.net.ConnectException ce) {
            LOG.fine("Server not reachable: " + url);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "HTTP request failed: " + url, e);
        }
        return null;
    }
}
