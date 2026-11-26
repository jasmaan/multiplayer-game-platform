package ca.ucalgary.seng300.server;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.notification.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server entry point. Wires all components together and starts both servers.
 *
 * Usage:
 *   java -cp <classpath> ca.ucalgary.seng300.server.ServerApp [wsPort] [httpPort]
 *
 * Defaults:
 *   WebSocket: 8080
 *   HTTP REST: 8081
 *
 * The HMAC secret for token signing is read from the environment variable
 * SERVER_SECRET. A hardcoded fallback is used for development only — always
 * set SERVER_SECRET in production.
 */
public class ServerApp {

    private static final Logger LOG = Logger.getLogger(ServerApp.class.getName());

    private static final int DEFAULT_WS_PORT   = 8080;
    private static final int DEFAULT_HTTP_PORT = 8081;

    public static void main(String[] args) {
        int wsPort   = args.length > 0 ? parsePort(args[0], DEFAULT_WS_PORT)   : DEFAULT_WS_PORT;
        int httpPort = args.length > 1 ? parsePort(args[1], DEFAULT_HTTP_PORT) : DEFAULT_HTTP_PORT;

        String secret = System.getenv("SERVER_SECRET");
        if (secret == null || secret.isBlank()) {
            LOG.warning("SERVER_SECRET not set — using insecure dev secret. DO NOT use in production.");
            secret = "dev-secret-change-me";
        }

        // ── Core platform (owns all game/room/session state) ───────────────────
        PlatformCore platform = new PlatformCore();

        // ── Auth & session registry ────────────────────────────────────────────
        AuthService     auth     = new AuthService(secret);
        ObjectMapper    mapper   = new ObjectMapper();
        SessionRegistry registry = new SessionRegistry(mapper);

        // ── Notification bridge: routes PlatformCore notifications to sockets ──
        NotificationAdapter adapter = new NotificationAdapter(registry, platform.getSessionManager());
        platform.getNotificationService().setListener(adapter);

        // ── Message router ─────────────────────────────────────────────────────
        MessageRouter router = new MessageRouter(platform, auth, registry);

        // ── WebSocket server ───────────────────────────────────────────────────
        GameWebSocketServer wsServer = new GameWebSocketServer(wsPort, router, registry, auth, platform);
        wsServer.start();

        // ── HTTP REST server ───────────────────────────────────────────────────
        HttpApiServer httpServer = new HttpApiServer(platform, auth);
        try {
            httpServer.start(httpPort);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start HTTP server on port " + httpPort, e);
            wsServer.shutdown();
            System.exit(1);
        }

        // ── Shutdown hook ──────────────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down servers...");
            httpServer.stop();
            wsServer.shutdown();
        }, "shutdown-hook"));

        LOG.info("GameDino server running — WS:" + wsPort + "  HTTP:" + httpPort);
    }

    private static int parsePort(String arg, int fallback) {
        try {
            int p = Integer.parseInt(arg);
            if (p > 0 && p < 65536) return p;
        } catch (NumberFormatException ignored) {}
        LOG.warning("Invalid port argument '" + arg + "', using default " + fallback);
        return fallback;
    }
}
