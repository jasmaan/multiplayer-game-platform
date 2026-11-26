package ca.ucalgary.seng300.server;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.core.PlayerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight REST API built on the JDK's built-in {@code com.sun.net.httpserver}.
 * No external HTTP library required.
 *
 * Endpoints:
 *   POST /auth/login        { username, password }  → { token, playerId }
 *   POST /auth/register     { firstName, lastName, password } → { username }
 *   GET  /leaderboard       → [ { username, rank, mmr, wins }, … ]
 *   GET  /rooms             → [ { roomId, gameType, host, isFull }, … ]
 *
 * All responses are JSON.  Error responses carry { "error": "<message>" }.
 */
public class HttpApiServer {

    private static final Logger LOG = Logger.getLogger(HttpApiServer.class.getName());

    private final PlatformCore platform;
    private final AuthService  auth;
    private final ObjectMapper mapper;
    private HttpServer         server;

    public HttpApiServer(PlatformCore platform, AuthService auth) {
        this.platform = platform;
        this.auth     = auth;
        this.mapper   = new ObjectMapper();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Returns the port the server is bound to (available after {@link #start}). */
    public int getPort() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/auth/login",    this::handleLogin);
        server.createContext("/auth/register", this::handleRegister);
        server.createContext("/leaderboard",   this::handleLeaderboard);
        server.createContext("/rooms",         this::handleRooms);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        LOG.info("HTTP API server started on port " + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ── Handlers ───────────────────────────────────────────────────────────────

    /** POST /auth/login */
    private void handleLogin(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        try {
            Map<?, ?> body = readBody(ex);
            String username = str(body, "username");
            String password = str(body, "password");
            if (username == null || password == null) {
                sendError(ex, 400, "username and password are required");
                return;
            }
            boolean ok = platform.login(username, password);
            if (!ok) {
                sendError(ex, 401, "Invalid credentials");
                return;
            }
            String token = auth.issue(username);
            sendJson(ex, 200, Map.of("token", token, "playerId", username));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Login handler error", e);
            sendError(ex, 500, "Internal server error");
        }
    }

    /** POST /auth/register */
    private void handleRegister(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        try {
            Map<?, ?> body = readBody(ex);
            String firstName = str(body, "firstName");
            String lastName  = str(body, "lastName");
            String password  = str(body, "password");
            if (firstName == null || lastName == null || password == null) {
                sendError(ex, 400, "firstName, lastName, and password are required");
                return;
            }
            String username = platform.registerPlayer(firstName, lastName, password);
            if (username == null) {
                sendError(ex, 422, "Registration failed — check password requirements");
                return;
            }
            sendJson(ex, 201, Map.of("username", username));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Register handler error", e);
            sendError(ex, 500, "Internal server error");
        }
    }

    /** GET /leaderboard */
    private void handleLeaderboard(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        try {
            List<PlayerProfile> profiles = platform.getAllProfiles();
            List<Map<String, Object>> rows = profiles.stream().map(p -> {
                Map<String, Object> row = new HashMap<>();
                row.put("username", p.getUsername());
                row.put("rank",     p.getCurrentRank());
                row.put("wins",     p.getTotalWins());
                row.put("mmr",      p.getTotalMmr());
                return row;
            }).toList();
            sendJson(ex, 200, rows);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Leaderboard handler error", e);
            sendError(ex, 500, "Internal server error");
        }
    }

    /** GET /rooms */
    private void handleRooms(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        try {
            List<Map<String, Object>> rows = platform.getRoomManager().getAllRooms(null).stream().map(r -> {
                Map<String, Object> row = new HashMap<>();
                row.put("roomId",   r.getRoomId());
                row.put("gameType", r.getGameType());
                row.put("host",     r.getHostId());
                row.put("isFull",   r.isFull());
                return row;
            }).toList();
            sendJson(ex, 200, rows);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Rooms handler error", e);
            sendError(ex, 500, "Internal server error");
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    private boolean requireMethod(HttpExchange ex, String method) throws IOException {
        if (!method.equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "Method not allowed");
            return false;
        }
        return true;
    }

    private Map<?, ?> readBody(HttpExchange ex) throws Exception {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        return mapper.readValue(bytes, Map.class);
    }

    private String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return (v instanceof String s && !s.isBlank()) ? s : null;
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, Map.of("error", message));
    }
}
