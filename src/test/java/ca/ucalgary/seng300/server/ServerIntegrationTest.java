package ca.ucalgary.seng300.server;

import ca.ucalgary.seng300.core.PlatformCore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the REST + WebSocket server.
 *
 * Both servers start on OS-assigned ports (port 0) so there are no conflicts.
 * The test order matters: later tests reuse the token and username registered
 * in the @BeforeAll setup phase.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServerIntegrationTest {

    // ── Infrastructure ─────────────────────────────────────────────────────────

    private static final ObjectMapper MAPPER   = new ObjectMapper();
    private static final HttpClient   HTTP     = HttpClient.newHttpClient();
    private static final long         WS_WAIT  = 2_000; // ms to wait for a WS frame

    private static PlatformCore        platform;
    private static AuthService         auth;
    private static SessionRegistry     registry;
    private static GameWebSocketServer wsServer;
    private static HttpApiServer       httpServer;

    static int wsPort;
    static int httpPort;

    /** Registered once in @BeforeAll — reused across tests. */
    static String user1;
    static String token1;

    /** Tracks every username registered during the test run so @AfterAll can delete them. */
    static final List<String> createdUsers = new ArrayList<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @BeforeAll
    static void startServers() throws Exception {
        platform = new PlatformCore();
        auth     = new AuthService("integration-test-secret");
        registry = new SessionRegistry(MAPPER);

        NotificationAdapter adapter = new NotificationAdapter(registry, platform.getSessionManager());
        platform.getNotificationService().setListener(adapter);

        MessageRouter router = new MessageRouter(platform, auth, registry);

        wsServer = new GameWebSocketServer(0, router, registry, auth, platform);
        wsServer.start();
        Thread.sleep(400); // let the server thread bind
        wsPort = wsServer.getPort();

        httpServer = new HttpApiServer(platform, auth);
        httpServer.start(0);
        httpPort = httpServer.getPort();

        // Pre-register the primary test user via the REST API
        Map<String, Object> reg = register("Alice", "Smith");
        assertNotNull(reg.get("username"), "Registration must return a username");
        user1 = (String) reg.get("username");

        // Login to get a token
        Map<String, Object> login = httpPost("/auth/login",
            Map.of("username", user1, "password", "Test@1234"));
        assertNotNull(login.get("token"), "Login must return a token");
        token1 = (String) login.get("token");
    }

    @AfterAll
    static void stopServers() {
        if (httpServer != null) httpServer.stop();
        if (wsServer  != null) wsServer.shutdown();

        // Remove all users created during the test run from disk so they don't
        // pollute the leaderboard or the data/ directory between runs.
        if (platform != null) {
            for (String username : createdUsers) {
                platform.getUserRepository().delete(username);
                platform.getDatabase().deleteData("profile:" + username);
                platform.getDatabase().deleteData("credentials:" + username);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HTTP REST tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    void register_returnsUsername() throws Exception {
        Map<String, Object> resp = register("Bob", "Jones");
        assertNotNull(resp.get("username"));
        String name = (String) resp.get("username");
        assertTrue(name.startsWith("bj"), "Username should start with first-initial + last name");
    }

    @Test @Order(2)
    void register_weakPassword_returnsError() throws Exception {
        // Bad password — registration will fail, no username returned, nothing to clean up
        Map<String, Object> resp = httpPost("/auth/register",
            Map.of("firstName", "Bad", "lastName", "Pass", "password", "weak"));
        assertNotNull(resp.get("error"), "Weak password should return an error body");
    }

    @Test @Order(3)
    void login_validCredentials_returnsToken() throws Exception {
        assertNotNull(token1, "Token from @BeforeAll must not be null");
        assertTrue(token1.contains("."), "HMAC token must contain a '.' separator");
    }

    @Test @Order(4)
    void login_wrongPassword_returns401() throws Exception {
        HttpResponse<String> resp = rawPost("/auth/login",
            Map.of("username", user1, "password", "WrongPassword!99"));
        assertEquals(401, resp.statusCode());
    }

    @Test @Order(5)
    void leaderboard_returnsList() throws Exception {
        String body = rawGet("/leaderboard").body();
        // JSON array
        assertTrue(body.trim().startsWith("["), "Leaderboard must return a JSON array");
    }

    @Test @Order(6)
    void rooms_returnsList() throws Exception {
        String body = rawGet("/rooms").body();
        assertTrue(body.trim().startsWith("["), "Rooms must return a JSON array");
    }

    @Test @Order(7)
    void unknownEndpoint_returns404() throws Exception {
        HttpResponse<String> resp = rawGet("/notarealpath");
        // com.sun.net.httpserver returns 404 for unregistered paths
        assertEquals(404, resp.statusCode());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WebSocket tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(10)
    void ws_authPing_validToken_returnsAuthOk() throws Exception {
        try (TestWsClient ws = new TestWsClient(wsPort)) {
            ws.connectBlocking(3, TimeUnit.SECONDS);
            ws.sendFrame("AUTH_PING", token1, null);

            Map<String, Object> resp = ws.nextOfType("AUTH_OK", WS_WAIT);
            assertNotNull(resp, "Server must respond with AUTH_OK for a valid token");
        }
    }

    @Test @Order(11)
    void ws_authPing_invalidToken_returnsError() throws Exception {
        try (TestWsClient ws = new TestWsClient(wsPort)) {
            ws.connectBlocking(3, TimeUnit.SECONDS);
            ws.sendFrame("AUTH_PING", "not.a.real.token", null);

            Map<String, Object> resp = ws.nextOfType("ERROR", WS_WAIT);
            assertNotNull(resp, "Invalid token must return an ERROR frame");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.get("payload");
            assertEquals("INVALID_TOKEN", payload.get("code"));
        }
    }

    @Test @Order(12)
    void ws_frameWithoutAuth_returnsNotAuthenticated() throws Exception {
        try (TestWsClient ws = new TestWsClient(wsPort)) {
            ws.connectBlocking(3, TimeUnit.SECONDS);
            // Send a real frame type without doing AUTH_PING first
            ws.sendFrame("JOIN_QUEUE", null, Map.of("gameType", "TicTacToe"));

            Map<String, Object> resp = ws.nextOfType("ERROR", WS_WAIT);
            assertNotNull(resp, "Unauthenticated frame must return an ERROR");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.get("payload");
            assertEquals("NOT_AUTHENTICATED", payload.get("code"));
        }
    }

    @Test @Order(13)
    void ws_malformedJson_returnsError() throws Exception {
        try (TestWsClient ws = new TestWsClient(wsPort)) {
            ws.connectBlocking(3, TimeUnit.SECONDS);
            ws.send("{not valid json{{{{");

            Map<String, Object> resp = ws.nextOfType("ERROR", WS_WAIT);
            assertNotNull(resp, "Bad JSON must return an ERROR frame");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.get("payload");
            assertEquals("BAD_JSON", payload.get("code"));
        }
    }

    @Test @Order(14)
    void ws_createRoom_returnsRoomCreated() throws Exception {
        // Need a fresh login for a fresh user so currentUser is set correctly
        Map<String, Object> reg = register("Carol", "Room");
        String uname = (String) reg.get("username");
        Map<String, Object> login = httpPost("/auth/login",
            Map.of("username", uname, "password", "Test@1234"));
        String tok = (String) login.get("token");

        try (TestWsClient ws = new TestWsClient(wsPort)) {
            ws.connectBlocking(3, TimeUnit.SECONDS);
            ws.sendFrame("AUTH_PING", tok, null);
            ws.nextOfType("AUTH_OK", WS_WAIT);

            ws.sendFrame("CREATE_ROOM", tok, Map.of("gameType", "TicTacToe"));
            Map<String, Object> resp = ws.nextOfType("ROOM_CREATED", WS_WAIT);
            assertNotNull(resp, "CREATE_ROOM must return ROOM_CREATED");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.get("payload");
            assertNotNull(payload.get("roomId"), "ROOM_CREATED payload must include roomId");
        }
    }

    @Test @Order(15)
    void ws_joinRoom_secondPlayer_returnsRoomJoined() throws Exception {
        // Register host and guest
        Map<String, Object> hostReg = register("Host", "Player");
        String hostName = (String) hostReg.get("username");
        String hostTok  = (String) httpPost("/auth/login",
            Map.of("username", hostName, "password", "Test@1234")).get("token");

        Map<String, Object> guestReg = register("Guest", "Player");
        String guestName = (String) guestReg.get("username");
        String guestTok  = (String) httpPost("/auth/login",
            Map.of("username", guestName, "password", "Test@1234")).get("token");

        try (TestWsClient host  = new TestWsClient(wsPort);
             TestWsClient guest = new TestWsClient(wsPort)) {

            host.connectBlocking(3, TimeUnit.SECONDS);
            host.sendFrame("AUTH_PING", hostTok, null);
            host.nextOfType("AUTH_OK", WS_WAIT);

            // Host creates a room
            host.sendFrame("CREATE_ROOM", hostTok, Map.of("gameType", "TicTacToe"));
            Map<String, Object> created = host.nextOfType("ROOM_CREATED", WS_WAIT);
            assertNotNull(created, "Host must receive ROOM_CREATED");
            @SuppressWarnings("unchecked")
            String roomId = (String) ((Map<String, Object>) created.get("payload")).get("roomId");

            // Guest connects and joins
            guest.connectBlocking(3, TimeUnit.SECONDS);
            guest.sendFrame("AUTH_PING", guestTok, null);
            guest.nextOfType("AUTH_OK", WS_WAIT);

            guest.sendFrame("JOIN_ROOM", guestTok, Map.of("roomId", roomId));
            Map<String, Object> joined = guest.nextOfType("ROOM_JOINED", WS_WAIT);
            assertNotNull(joined, "Guest must receive ROOM_JOINED");
        }
    }

    @Test @Order(16)
    void ws_joinQueue_returnsQueueJoined() throws Exception {
        // Register a fresh user for this test
        Map<String, Object> reg = register("Queue", "Tester");
        String uname = (String) reg.get("username");
        String tok   = (String) httpPost("/auth/login",
            Map.of("username", uname, "password", "Test@1234")).get("token");

        try (TestWsClient ws = new TestWsClient(wsPort)) {
            ws.connectBlocking(3, TimeUnit.SECONDS);
            ws.sendFrame("AUTH_PING", tok, null);
            ws.nextOfType("AUTH_OK", WS_WAIT);

            ws.sendFrame("JOIN_QUEUE", tok, Map.of("gameType", "TicTacToe"));
            Map<String, Object> resp = ws.nextOfType("QUEUE_JOINED", WS_WAIT);
            assertNotNull(resp, "JOIN_QUEUE must return QUEUE_JOINED");

            // Leave queue so we don't pollute later tests
            ws.sendFrame("LEAVE_QUEUE", tok, null);
            ws.nextOfType("QUEUE_LEFT", WS_WAIT);
        }
    }

    @Test @Order(17)
    void ws_matchmaking_twoPlayers_receivesMatchFound() throws Exception {
        // Two players join the queue — the server should auto-match them
        Map<String, Object> r1 = register("Match", "One");
        Map<String, Object> r2 = register("Match", "Two");

        String u1 = (String) r1.get("username");
        String u2 = (String) r2.get("username");
        String t1 = (String) httpPost("/auth/login", Map.of("username", u1, "password", "Test@1234")).get("token");
        String t2 = (String) httpPost("/auth/login", Map.of("username", u2, "password", "Test@1234")).get("token");

        try (TestWsClient ws1 = new TestWsClient(wsPort);
             TestWsClient ws2 = new TestWsClient(wsPort)) {

            ws1.connectBlocking(3, TimeUnit.SECONDS);
            ws1.sendFrame("AUTH_PING", t1, null);
            ws1.nextOfType("AUTH_OK", WS_WAIT);

            ws2.connectBlocking(3, TimeUnit.SECONDS);
            ws2.sendFrame("AUTH_PING", t2, null);
            ws2.nextOfType("AUTH_OK", WS_WAIT);

            // Player 1 joins first — no match yet, QUEUE_JOINED arrives before any MATCH_FOUND
            ws1.sendFrame("JOIN_QUEUE", t1, Map.of("gameType", "ConnectFour"));
            ws1.nextOfType("QUEUE_JOINED", WS_WAIT);

            // Player 2 joins — triggers match. Server pushes MATCH_FOUND to ws1 & ws2,
            // then responds QUEUE_JOINED to ws2. DON'T wait for QUEUE_JOINED on ws2 here —
            // nextOfType would discard the MATCH_FOUND frame that arrives first.
            ws2.sendFrame("JOIN_QUEUE", t2, Map.of("gameType", "ConnectFour"));

            // Both should receive MATCH_FOUND
            Map<String, Object> mf1 = ws1.nextOfType("MATCH_FOUND", 3_000);
            Map<String, Object> mf2 = ws2.nextOfType("MATCH_FOUND", 3_000);
            assertNotNull(mf1, "Player 1 must receive MATCH_FOUND");
            assertNotNull(mf2, "Player 2 must receive MATCH_FOUND");
        }
    }

    @Test @Order(18)
    void ws_submitTicTacToeMove_accepted() throws Exception {
        // Set up two players, create a room, start a session, submit a move
        Map<String, Object> r1 = register("Move", "One");
        Map<String, Object> r2 = register("Move", "Two");
        String u1 = (String) r1.get("username");
        String u2 = (String) r2.get("username");
        String t1 = (String) httpPost("/auth/login", Map.of("username", u1, "password", "Test@1234")).get("token");
        String t2 = (String) httpPost("/auth/login", Map.of("username", u2, "password", "Test@1234")).get("token");

        try (TestWsClient ws1 = new TestWsClient(wsPort);
             TestWsClient ws2 = new TestWsClient(wsPort)) {

            ws1.connectBlocking(3, TimeUnit.SECONDS);
            ws1.sendFrame("AUTH_PING", t1, null);
            ws1.nextOfType("AUTH_OK", WS_WAIT);

            ws2.connectBlocking(3, TimeUnit.SECONDS);
            ws2.sendFrame("AUTH_PING", t2, null);
            ws2.nextOfType("AUTH_OK", WS_WAIT);

            // Host creates room
            ws1.sendFrame("CREATE_ROOM", t1, Map.of("gameType", "TicTacToe"));
            @SuppressWarnings("unchecked")
            String roomId = (String)
                ((Map<String, Object>) ws1.nextOfType("ROOM_CREATED", WS_WAIT).get("payload")).get("roomId");

            // Guest joins
            ws2.sendFrame("JOIN_ROOM", t2, Map.of("roomId", roomId));
            ws2.nextOfType("ROOM_JOINED", WS_WAIT);

            // Start the session directly through PlatformCore's SessionManager
            // (RoomManager has its own internal SessionManager — use platform's so submitMove() can reach it)
            ca.ucalgary.seng300.room.Room room18 = platform.getRoomManager().getRoom(roomId);
            String sessionId = platform.getSessionManager().startSession(
                roomId, room18.getGameType(), room18.getPlayers());
            assertNotNull(sessionId, "A game session must exist after startSession");

            // Player 1 submits a TicTacToe move (row=0, col=0, piece=X)
            String moveId = UUID.randomUUID().toString();
            ws1.sendFrame("SUBMIT_MOVE", t1, Map.of(
                "sessionId", sessionId,
                "moveId",    moveId,
                "gameType",  "TicTacToe",
                "moveData",  Map.of("row", 0, "col", 0, "piece", "X")
            ));

            Map<String, Object> ack = ws1.nextOfType("MOVE_ACCEPTED", WS_WAIT);
            assertNotNull(ack, "Valid TicTacToe move must be accepted");
        }
    }

    @Test @Order(19)
    void ws_submitMove_duplicateMoveId_returnsDuplicate() throws Exception {
        Map<String, Object> r1 = register("Dup", "One");
        Map<String, Object> r2 = register("Dup", "Two");
        String u1 = (String) r1.get("username");
        String u2 = (String) r2.get("username");
        String t1 = (String) httpPost("/auth/login", Map.of("username", u1, "password", "Test@1234")).get("token");
        String t2 = (String) httpPost("/auth/login", Map.of("username", u2, "password", "Test@1234")).get("token");

        try (TestWsClient ws1 = new TestWsClient(wsPort);
             TestWsClient ws2 = new TestWsClient(wsPort)) {

            ws1.connectBlocking(3, TimeUnit.SECONDS);
            ws1.sendFrame("AUTH_PING", t1, null);
            ws1.nextOfType("AUTH_OK", WS_WAIT);

            ws2.connectBlocking(3, TimeUnit.SECONDS);
            ws2.sendFrame("AUTH_PING", t2, null);
            ws2.nextOfType("AUTH_OK", WS_WAIT);

            ws1.sendFrame("CREATE_ROOM", t1, Map.of("gameType", "TicTacToe"));
            @SuppressWarnings("unchecked")
            String roomId = (String)
                ((Map<String, Object>) ws1.nextOfType("ROOM_CREATED", WS_WAIT).get("payload")).get("roomId");

            ws2.sendFrame("JOIN_ROOM", t2, Map.of("roomId", roomId));
            ws2.nextOfType("ROOM_JOINED", WS_WAIT);

            ca.ucalgary.seng300.room.Room room19 = platform.getRoomManager().getRoom(roomId);
            String sessionId19 = platform.getSessionManager().startSession(
                roomId, room19.getGameType(), room19.getPlayers());
            assertNotNull(sessionId19, "Session must start for duplicate-move test");

            String moveId = UUID.randomUUID().toString();
            Map<String, Object> moveFrame = Map.of(
                "sessionId", sessionId19,
                "moveId",    moveId,
                "gameType",  "TicTacToe",
                "moveData",  Map.of("row", 1, "col", 1, "piece", "X")
            );

            ws1.sendFrame("SUBMIT_MOVE", t1, moveFrame);
            ws1.nextOfType("MOVE_ACCEPTED", WS_WAIT); // consume first acceptance

            // Send the same moveId again
            ws1.sendFrame("SUBMIT_MOVE", t1, moveFrame);
            Map<String, Object> dupResp = ws1.nextOfType("MOVE_DUPLICATE", WS_WAIT);
            assertNotNull(dupResp, "Duplicate moveId must return MOVE_DUPLICATE");
        }
    }

    @Test @Order(20)
    void ws_forfeit_returnsAck() throws Exception {
        Map<String, Object> r1 = register("Forfeit", "One");
        Map<String, Object> r2 = register("Forfeit", "Two");
        String u1 = (String) r1.get("username");
        String u2 = (String) r2.get("username");
        String t1 = (String) httpPost("/auth/login", Map.of("username", u1, "password", "Test@1234")).get("token");
        String t2 = (String) httpPost("/auth/login", Map.of("username", u2, "password", "Test@1234")).get("token");

        try (TestWsClient ws1 = new TestWsClient(wsPort);
             TestWsClient ws2 = new TestWsClient(wsPort)) {

            ws1.connectBlocking(3, TimeUnit.SECONDS);
            ws1.sendFrame("AUTH_PING", t1, null);
            ws1.nextOfType("AUTH_OK", WS_WAIT);

            ws2.connectBlocking(3, TimeUnit.SECONDS);
            ws2.sendFrame("AUTH_PING", t2, null);
            ws2.nextOfType("AUTH_OK", WS_WAIT);

            ws1.sendFrame("CREATE_ROOM", t1, Map.of("gameType", "Checkers"));
            @SuppressWarnings("unchecked")
            String roomId = (String)
                ((Map<String, Object>) ws1.nextOfType("ROOM_CREATED", WS_WAIT).get("payload")).get("roomId");

            ws2.sendFrame("JOIN_ROOM", t2, Map.of("roomId", roomId));
            ws2.nextOfType("ROOM_JOINED", WS_WAIT);

            ca.ucalgary.seng300.room.Room room20 = platform.getRoomManager().getRoom(roomId);
            String sessionId20 = platform.getSessionManager().startSession(
                roomId, room20.getGameType(), room20.getPlayers());
            assertNotNull(sessionId20, "Session must start for forfeit test");

            ws1.sendFrame("FORFEIT", t1, Map.of("sessionId", sessionId20));
            Map<String, Object> ack = ws1.nextOfType("FORFEIT_ACK", WS_WAIT);
            assertNotNull(ack, "FORFEIT must return FORFEIT_ACK");
        }
    }

    @Test @Order(21)
    void ws_rateLimiter_blocksExcessiveMessages() throws Exception {
        // Register a dedicated user for this test
        Map<String, Object> reg = register("Rate", "Limit");
        String uname = (String) reg.get("username");
        String tok   = (String) httpPost("/auth/login",
            Map.of("username", uname, "password", "Test@1234")).get("token");

        try (TestWsClient ws = new TestWsClient(wsPort)) {
            ws.connectBlocking(3, TimeUnit.SECONDS);
            ws.sendFrame("AUTH_PING", tok, null);
            ws.nextOfType("AUTH_OK", WS_WAIT);

            // Flood 25 frames — the 21st onward should trigger RATE_LIMITED
            for (int i = 0; i < 25; i++) {
                ws.sendFrame("LEAVE_QUEUE", tok, null);
            }

            // Drain all responses and look for at least one RATE_LIMITED
            boolean rateLimited = false;
            long deadline = System.currentTimeMillis() + 3_000;
            while (System.currentTimeMillis() < deadline) {
                Map<String, Object> frame = ws.received.poll(200, TimeUnit.MILLISECONDS);
                if (frame == null) break;
                if ("ERROR".equals(frame.get("type"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) frame.get("payload");
                    if ("RATE_LIMITED".equals(p.get("code"))) {
                        rateLimited = true;
                        break;
                    }
                }
            }
            assertTrue(rateLimited, "Sending 25 frames must trigger at least one RATE_LIMITED error");
        }
    }

    @Test @Order(22)
    void ws_sendChat_deliversToBothPlayers() throws Exception {
        Map<String, Object> r1 = register("Chat", "One");
        Map<String, Object> r2 = register("Chat", "Two");
        String u1 = (String) r1.get("username");
        String u2 = (String) r2.get("username");
        String t1 = (String) httpPost("/auth/login", Map.of("username", u1, "password", "Test@1234")).get("token");
        String t2 = (String) httpPost("/auth/login", Map.of("username", u2, "password", "Test@1234")).get("token");

        try (TestWsClient ws1 = new TestWsClient(wsPort);
             TestWsClient ws2 = new TestWsClient(wsPort)) {

            ws1.connectBlocking(3, TimeUnit.SECONDS);
            ws1.sendFrame("AUTH_PING", t1, null);
            ws1.nextOfType("AUTH_OK", WS_WAIT);

            ws2.connectBlocking(3, TimeUnit.SECONDS);
            ws2.sendFrame("AUTH_PING", t2, null);
            ws2.nextOfType("AUTH_OK", WS_WAIT);

            ws1.sendFrame("CREATE_ROOM", t1, Map.of("gameType", "TicTacToe"));
            @SuppressWarnings("unchecked")
            String roomId = (String)
                ((Map<String, Object>) ws1.nextOfType("ROOM_CREATED", WS_WAIT).get("payload")).get("roomId");

            ws2.sendFrame("JOIN_ROOM", t2, Map.of("roomId", roomId));
            ws2.nextOfType("ROOM_JOINED", WS_WAIT);

            ca.ucalgary.seng300.room.Room room22 = platform.getRoomManager().getRoom(roomId);
            String sessionId22 = platform.getSessionManager().startSession(
                roomId, room22.getGameType(), room22.getPlayers());
            assertNotNull(sessionId22, "Session must start for chat test");

            ws1.sendFrame("SEND_CHAT", t1, Map.of("sessionId", sessionId22, "text", "Hello!"));

            // Both players should receive CHAT_MESSAGE
            Map<String, Object> chat1 = ws1.nextOfType("CHAT_MESSAGE", WS_WAIT);
            Map<String, Object> chat2 = ws2.nextOfType("CHAT_MESSAGE", WS_WAIT);
            assertNotNull(chat1, "Sender must receive their own CHAT_MESSAGE echo");
            assertNotNull(chat2, "Recipient must receive CHAT_MESSAGE");

            @SuppressWarnings("unchecked")
            String text = (String) ((Map<String, Object>) chat2.get("payload")).get("text");
            assertEquals("Hello!", text);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper: lightweight WebSocket test client
    // ══════════════════════════════════════════════════════════════════════════

    static class TestWsClient extends WebSocketClient implements AutoCloseable {

        final BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

        TestWsClient(int port) throws Exception {
            super(new URI("ws://localhost:" + port));
        }

        @Override public void onOpen(ServerHandshake h) {}

        @Override
        public void onMessage(String raw) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> frame = MAPPER.readValue(raw,
                    new TypeReference<Map<String, Object>>() {});
                received.add(frame);
            } catch (Exception e) {
                System.err.println("TestWsClient: could not parse frame: " + raw);
            }
        }

        @Override public void onClose(int code, String reason, boolean remote) {}
        @Override public void onError(Exception e) {}

        /** Send a typed frame (token and payload are optional). */
        void sendFrame(String type, String token, Object payload) throws Exception {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", type);
            if (token   != null) frame.put("token",   token);
            if (payload != null) frame.put("payload", payload);
            send(MAPPER.writeValueAsString(frame));
        }

        /**
         * Poll the received queue until a frame with the expected type arrives,
         * skipping any unrelated frames (e.g. NOTIFICATION, PING).
         */
        Map<String, Object> nextOfType(String expectedType, long timeoutMs)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                Map<String, Object> frame = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (frame == null) break;
                if (expectedType.equals(frame.get("type"))) return frame;
            }
            return null;
        }

        /** AutoCloseable: initiates a graceful close (non-blocking; sufficient for test cleanup). */
        @Override
        public void close() {
            super.close();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper: HTTP utilities
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Register a test user and record their username so @AfterAll can clean them up.
     */
    static Map<String, Object> register(String firstName, String lastName) throws Exception {
        Map<String, Object> resp = httpPost("/auth/register",
            Map.of("firstName", firstName, "lastName", lastName, "password", "Test@1234"));
        String username = (String) resp.get("username");
        if (username != null) createdUsers.add(username);
        return resp;
    }

    static Map<String, Object> httpPost(String path, Map<String, Object> body) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + httpPort + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        String respBody = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return MAPPER.readValue(respBody, new TypeReference<Map<String, Object>>() {});
    }

    static HttpResponse<String> rawPost(String path, Map<String, Object> body) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + httpPort + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> rawGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + httpPort + path))
            .GET()
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
