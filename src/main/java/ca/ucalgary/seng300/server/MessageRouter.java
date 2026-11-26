package ca.ucalgary.seng300.server;

import ca.ucalgary.seng300.core.PlatformCore;
import ca.ucalgary.seng300.core.PlatformError;
import ca.ucalgary.seng300.core.PlayerProfile;
import ca.ucalgary.seng300.events.ChatMessageEvent;
import ca.ucalgary.seng300.games.Board;
import ca.ucalgary.seng300.games.BoardSerializer;
import ca.ucalgary.seng300.games.CheckersMove;
import ca.ucalgary.seng300.games.ConnectFourMove;
import ca.ucalgary.seng300.games.Piece;
import ca.ucalgary.seng300.games.TicTacToeMove;
import ca.ucalgary.seng300.room.Room;
import ca.ucalgary.seng300.session.GameSession;
import ca.ucalgary.seng300.server.dto.InboundMessage;
import ca.ucalgary.seng300.server.dto.OutboundMessage;
import ca.ucalgary.seng300.server.dto.payloads.ChatPayload;
import ca.ucalgary.seng300.server.dto.payloads.MovePayload;
import ca.ucalgary.seng300.server.dto.payloads.QueuePayload;
import ca.ucalgary.seng300.server.dto.payloads.RoomPayload;
import ca.ucalgary.seng300.server.dto.payloads.SessionPayload;
import ca.ucalgary.seng300.session.MoveSubmissionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deserializes inbound WebSocket frames, validates authentication and rate limits,
 * dispatches to PlatformCore, and writes typed responses back via SessionRegistry.
 *
 * One instance is shared across all connections — all state lives in PlatformCore /
 * SessionRegistry (both are thread-safe).
 */
public class MessageRouter {

    private static final Logger LOG = Logger.getLogger(MessageRouter.class.getName());

    private final PlatformCore    platform;
    private final AuthService     auth;
    private final SessionRegistry registry;
    private final ObjectMapper    mapper;

    public MessageRouter(PlatformCore platform, AuthService auth, SessionRegistry registry) {
        this.platform = platform;
        this.auth     = auth;
        this.registry = registry;
        this.mapper   = new ObjectMapper();
        // TicTacToeMove uses package-private fields — allow field access
        this.mapper.setVisibility(
            com.fasterxml.jackson.databind.introspect.VisibilityChecker.Std.defaultInstance()
                .withFieldVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY));
        this.mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    /**
     * Called by GameWebSocketServer for every inbound text frame.
     * @param socket  the connection that sent the frame
     * @param rawJson the raw JSON string
     */
    public void route(WebSocket socket, String rawJson) {
        InboundMessage msg;
        try {
            msg = mapper.readValue(rawJson, InboundMessage.class);
        } catch (Exception e) {
            send(socket, OutboundMessage.error("BAD_JSON", "Malformed JSON frame"));
            return;
        }

        String type = msg.getType();
        if (type == null) {
            send(socket, OutboundMessage.error("MISSING_TYPE", "Frame missing 'type' field"));
            return;
        }

        // AUTH_PING is the one frame that doesn't require a prior registration
        if ("AUTH_PING".equals(type)) {
            handleAuthPing(socket, msg.getToken());
            return;
        }

        // All other frames require a valid, registered session
        String playerId = registry.getPlayerId(socket);
        if (playerId == null) {
            send(socket, OutboundMessage.error("NOT_AUTHENTICATED", "Send AUTH_PING first"));
            return;
        }

        // Rate-limit check
        if (!registry.checkRateLimit(playerId)) {
            send(socket, OutboundMessage.error("RATE_LIMITED", "Too many messages — slow down"));
            return;
        }

        // Dispatch by message type
        try {
            switch (type) {
                case "JOIN_QUEUE"          -> handleJoinQueue(socket, playerId, msg.getPayload());
                case "LEAVE_QUEUE"         -> handleLeaveQueue(socket, playerId);
                case "CREATE_ROOM"         -> handleCreateRoom(socket, playerId, msg.getPayload());
                case "JOIN_ROOM"           -> handleJoinRoom(socket, playerId, msg.getPayload());
                case "LEAVE_ROOM"          -> handleLeaveRoom(socket, playerId, msg.getPayload());
                case "SUBMIT_MOVE"         -> handleSubmitMove(socket, playerId, msg.getPayload());
                case "FORFEIT"             -> handleForfeit(socket, playerId, msg.getPayload());
                case "SEND_CHAT"           -> handleChat(socket, playerId, msg.getPayload());
                case "START_GAME"          -> handleStartGame(socket, playerId, msg.getPayload());
                case "ACCEPT_MATCH"           -> handleAcceptMatch(socket, playerId, msg.getPayload());
                case "DECLINE_MATCH"          -> handleDeclineMatch(socket, playerId, msg.getPayload());
                case "REQUEST_PLAY_AGAIN"     -> handleRequestPlayAgain(socket, playerId, msg.getPayload());
                case "CANCEL_PLAY_AGAIN"      -> handleCancelPlayAgain(socket, playerId, msg.getPayload());
                case "NOTIFY_OPPONENT_ENDED"  -> handleNotifyOpponentEnded(socket, playerId, msg.getPayload());
                case "SEND_FRIEND_REQUEST"    -> handleSendFriendRequest(socket, playerId, msg.getPayload());
                default -> send(socket, OutboundMessage.error("UNKNOWN_TYPE", "Unrecognised frame type: " + type));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled error in MessageRouter.route()", e);
            send(socket, OutboundMessage.error("INTERNAL_ERROR", "Unexpected server error"));
        }
    }

    // ── AUTH_PING ──────────────────────────────────────────────────────────────

    private void handleAuthPing(WebSocket socket, String token) {
        if (token == null) {
            send(socket, OutboundMessage.error("MISSING_TOKEN", "AUTH_PING requires a token"));
            return;
        }
        String playerId = auth.validate(token);
        if (playerId == null) {
            send(socket, OutboundMessage.error("INVALID_TOKEN", "Token expired or invalid"));
            return;
        }
        auth.refresh(token);
        registry.register(playerId, socket);
        send(socket, OutboundMessage.of("AUTH_OK", Map.of("playerId", playerId)));
    }

    // ── Queue ──────────────────────────────────────────────────────────────────

    private void handleJoinQueue(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        QueuePayload qp = parse(payload, QueuePayload.class);
        if (qp.getGameType() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "gameType is required"));
            return;
        }
        platform.joinQueue(playerId, qp.getGameType());
        send(socket, OutboundMessage.of("QUEUE_JOINED", Map.of("gameType", qp.getGameType())));
    }

    private void handleLeaveQueue(WebSocket socket, String playerId) {
        platform.leaveQueue(playerId);
        send(socket, OutboundMessage.of("QUEUE_LEFT", Map.of("playerId", playerId)));
    }

    // ── Room ───────────────────────────────────────────────────────────────────

    private void handleCreateRoom(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        RoomPayload rp = parse(payload, RoomPayload.class);
        if (rp.getGameType() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "gameType is required"));
            return;
        }
        Room room = platform.getRoomManager().createRoom(playerId, rp.getGameType(), 2);
        send(socket, OutboundMessage.of("ROOM_CREATED", Map.of("roomId", room.getRoomId())));
    }

    private void handleJoinRoom(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        RoomPayload rp = parse(payload, RoomPayload.class);
        if (rp.getRoomId() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "roomId is required"));
            return;
        }
        PlatformError err = platform.joinRoom(rp.getRoomId(), playerId);
        if (err != null) {
            send(socket, OutboundMessage.error(err.name(), err.name().toLowerCase().replace('_', ' ')));
        } else {
            send(socket, OutboundMessage.of("ROOM_JOINED", Map.of("roomId", rp.getRoomId())));
        }
    }

    private void handleLeaveRoom(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        RoomPayload rp = parse(payload, RoomPayload.class);
        if (rp.getRoomId() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "roomId is required"));
            return;
        }
        platform.getRoomManager().leaveRoom(rp.getRoomId(), playerId);
        send(socket, OutboundMessage.of("ROOM_LEFT", Map.of("roomId", rp.getRoomId())));
    }

    // ── Move ───────────────────────────────────────────────────────────────────

    private void handleSubmitMove(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        MovePayload mp = parse(payload, MovePayload.class);

        if (mp.getSessionId() == null || mp.getGameType() == null || mp.getMoveData() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "sessionId, gameType, and moveData are required"));
            return;
        }

        // Idempotency — skip if this moveId was already applied
        if (registry.isDuplicateMove(mp.getSessionId(), mp.getMoveId())) {
            send(socket, OutboundMessage.of("MOVE_DUPLICATE", Map.of("moveId", mp.getMoveId())));
            return;
        }

        Object move = deserializeMove(mp.getGameType(), mp.getMoveData());
        if (move == null) {
            send(socket, OutboundMessage.error("BAD_MOVE", "Could not parse moveData for gameType: " + mp.getGameType()));
            return;
        }

        MoveSubmissionResult result = platform.submitMove(mp.getSessionId(), playerId, move);
        if (result == null || !result.isAccepted()) {
            String reason = (result != null && result.getRejection() != null)
                ? result.getRejection().getMessage() : "Move rejected";
            send(socket, OutboundMessage.error("MOVE_REJECTED", reason));
            return;
        }

        registry.recordMoveId(mp.getSessionId(), mp.getMoveId());

        // Build a minimal state summary to echo back to the mover
        Map<String, Object> ack = new HashMap<>();
        ack.put("sessionId", mp.getSessionId());
        ack.put("moveId",    mp.getMoveId());
        ack.put("gameOver",  result.getUpdate().isGameOver());
        if (result.getUpdate().isGameOver()) {
            ack.put("winnerId", result.getUpdate().getWinnerId());
        } else {
            ack.put("nextPlayerId", result.getUpdate().getActivePlayerId());
        }
        send(socket, OutboundMessage.of("MOVE_ACCEPTED", ack));

        // Push a full GAME_STATE frame (with the serialized board) to ALL session players.
        // This lets each client re-render the board without querying a local session.
        GameSession session = platform.getSessionManager().getSession(mp.getSessionId());
        if (session != null) {
            Map<String, Object> state = new HashMap<>();
            state.put("sessionId",    mp.getSessionId());
            state.put("moveId",       mp.getMoveId());
            state.put("gameOver",     result.getUpdate().isGameOver());
            state.put("activePlayer", result.getUpdate().getActivePlayerId());
            if (result.getUpdate().isGameOver()) {
                state.put("winnerId", result.getUpdate().getWinnerId());
            }
            Object board = result.getUpdate().getPayload();
            if (board instanceof Board b) {
                state.put("board", BoardSerializer.serialize(b));
            }
            registry.pushToAll(session.getPlayers(), OutboundMessage.of("GAME_STATE", state));
        }
    }

    // ── Forfeit ────────────────────────────────────────────────────────────────

    private void handleForfeit(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        SessionPayload sp = parse(payload, SessionPayload.class);
        if (sp.getSessionId() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "sessionId is required"));
            return;
        }
        platform.forfeitGame(sp.getSessionId(), playerId);
        send(socket, OutboundMessage.of("FORFEIT_ACK", Map.of("sessionId", sp.getSessionId())));
        // Notify all session players so the opponent gets the win screen
        GameSession session = platform.getSessionManager().getSession(sp.getSessionId());
        if (session != null) {
            String winnerId = session.getPlayers().stream()
                    .filter(p -> !p.equals(playerId))
                    .findFirst().orElse(null);
            Map<String, Object> notif = new HashMap<>();
            notif.put("sessionId",  sp.getSessionId());
            notif.put("forfeitedBy", playerId);
            notif.put("winnerId",   winnerId);
            registry.pushToAll(session.getPlayers(), OutboundMessage.of("PLAYER_FORFEITED", notif));
        }
    }

    private void handleNotifyOpponentEnded(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        SessionPayload sp = parse(payload, SessionPayload.class);
        if (sp.getSessionId() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "sessionId is required"));
            return;
        }
        // Cancel any pending play-again state, which fires the waiting callback if opponent was waiting
        platform.cancelPlayAgain(sp.getSessionId(), playerId);
        // Also push directly so opponent is notified even if they hadn't clicked Play Again yet
        GameSession session = platform.getSessionManager().getSession(sp.getSessionId());
        if (session != null) {
            List<String> opponents = session.getPlayers().stream()
                    .filter(p -> !p.equals(playerId))
                    .toList();
            registry.pushToAll(opponents, OutboundMessage.of("OPPONENT_ENDED_MATCH",
                    Map.of("sessionId", sp.getSessionId())));
        }
    }

    // ── Match accept / decline handshake ──────────────────────────────────────

    /**
     * Player clicked "Accept" on the match-found modal.
     * When all players in the pending match have accepted, the session starts and
     * MATCH_CONFIRMED is pushed to everyone. Until then, ACCEPT_ACK is sent back.
     */
    private void handleAcceptMatch(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        String matchId = payload != null && payload.has("matchId") ? payload.get("matchId").asText() : null;
        if (matchId == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "matchId is required"));
            return;
        }
        SessionRegistry.PendingMatch pm = registry.getPendingMatch(matchId);
        if (pm == null) {
            // Match was cancelled by another player's decline/timeout
            send(socket, OutboundMessage.error("MATCH_EXPIRED", "Match is no longer available"));
            return;
        }
        pm.accepted.add(playerId);
        if (pm.accepted.containsAll(pm.players)) {
            registry.removePendingMatch(matchId);
            String sessionId = platform.getSessionManager().startSession(pm.roomId, pm.gameType, pm.players);
            if (sessionId == null) {
                registry.pushToAll(pm.players, OutboundMessage.error("SESSION_START_FAILED", "Could not start game session"));
                return;
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("sessionId",   sessionId);
            resp.put("gameType",    pm.gameType);
            resp.put("players",     pm.players);
            resp.put("firstPlayer", pm.players.isEmpty() ? null : pm.players.get(0));
            registry.pushToAll(pm.players, OutboundMessage.of("MATCH_CONFIRMED", resp));
        } else {
            send(socket, OutboundMessage.of("ACCEPT_ACK", Map.of("matchId", matchId)));
        }
    }

    /**
     * Player clicked "Decline" or timed out on the match-found modal.
     * Cancels the pending match, notifies ALL players (so open modals close),
     * and re-queues everyone so they continue searching for a new match.
     */
    private void handleDeclineMatch(WebSocket socket, String playerId, JsonNode payload) {
        String matchId = payload != null && payload.has("matchId") ? payload.get("matchId").asText() : null;
        if (matchId == null) return;
        SessionRegistry.PendingMatch pm = registry.removePendingMatch(matchId);
        if (pm == null) return; // already resolved by a concurrent decline
        // Notify every player (some may not have accepted yet and need their modal to close)
        registry.pushToAll(pm.players, OutboundMessage.of("MATCH_DECLINED", Map.of("matchId", matchId)));
        // Re-queue all players so they resume searching instead of getting stuck
        for (String p : pm.players) {
            platform.joinQueue(p, pm.gameType);
        }
    }

    // ── Start Game (room-based) ────────────────────────────────────────────────

    /**
     * Host clicked "Start Game" in network mode.
     * Starts the session on the server and pushes GAME_STARTED to all room players.
     */
    private void handleStartGame(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        RoomPayload rp = parse(payload, RoomPayload.class);
        if (rp.getRoomId() == null || rp.getGameType() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "roomId and gameType are required"));
            return;
        }
        Room room = platform.getRoomManager().getAllRooms(null).stream()
                .filter(r -> r.getRoomId().equals(rp.getRoomId()))
                .findFirst().orElse(null);
        if (room == null) {
            send(socket, OutboundMessage.error("ROOM_NOT_FOUND", "Room does not exist"));
            return;
        }
        if (!room.isFull()) {
            send(socket, OutboundMessage.error("ROOM_NOT_FULL", "Room is not full yet"));
            return;
        }
        String sessionId = platform.getSessionManager()
                .startSession(rp.getRoomId(), rp.getGameType(), room.getPlayers());
        if (sessionId == null) {
            send(socket, OutboundMessage.error("SESSION_START_FAILED", "Could not start game session"));
            return;
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("sessionId",   sessionId);
        resp.put("gameType",    rp.getGameType());
        resp.put("players",     room.getPlayers());
        resp.put("firstPlayer", room.getPlayers().isEmpty() ? null : room.getPlayers().get(0));
        registry.pushToAll(room.getPlayers(), OutboundMessage.of("GAME_STARTED", resp));
    }

    // ── Chat ───────────────────────────────────────────────────────────────────

    private void handleChat(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        ChatPayload cp = parse(payload, ChatPayload.class);
        if (cp.getSessionId() == null || cp.getText() == null || cp.getText().isBlank()) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "sessionId and text are required"));
            return;
        }
        // NotificationAdapter.onChatMessage() delivers CHAT_MESSAGE frames to all session players.
        // No chatCallback needed here — Platform.runLater() wrapping makes those GUI-only.
        platform.sendChatMessage(cp.getSessionId(), playerId, cp.getText());
    }

    // ── Play-again ─────────────────────────────────────────────────────────────

    private void handleRequestPlayAgain(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        SessionPayload sp = parse(payload, SessionPayload.class);
        if (sp.getSessionId() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "sessionId is required"));
            return;
        }
        boolean bothReady = platform.requestPlayAgain(sp.getSessionId(), playerId, () -> {
            // Fired when the opponent clicks "End Match" — tell this player to navigate away
            send(socket, OutboundMessage.of("OPPONENT_ENDED_MATCH",
                Map.of("sessionId", sp.getSessionId())));
        });
        if (bothReady) {
            // Both sides clicked "Play Again" — start a new session and send GAME_STARTED to both.
            GameSession old = platform.getSessionManager().getSession(sp.getSessionId());
            if (old != null) {
                String gameType  = old.getGameType();
                List<String> players = old.getPlayers();
                // Create a transient room to satisfy startSession's room-handshake contract.
                Room rematchRoom = platform.getRoomManager().createRoom(players.get(0), gameType, players.size());
                for (int i = 1; i < players.size(); i++) {
                    platform.getRoomManager().joinRoom(rematchRoom.getRoomId(), players.get(i));
                }
                String newSessionId = platform.getSessionManager()
                        .startSession(rematchRoom.getRoomId(), gameType, players);
                // Room lifecycle ends at game start.
                for (String p : players) {
                    platform.getRoomManager().leaveRoom(rematchRoom.getRoomId(), p);
                }
                if (newSessionId != null) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("sessionId",   newSessionId);
                    resp.put("gameType",    gameType);
                    resp.put("players",     players);
                    resp.put("firstPlayer", players.isEmpty() ? null : players.get(0));
                    registry.pushToAll(players, OutboundMessage.of("GAME_STARTED", resp));
                    return;
                }
            }
            // Fallback if session was already cleaned up
            send(socket, OutboundMessage.error("REMATCH_FAILED", "Could not start rematch session"));
        } else {
            send(socket, OutboundMessage.of("PLAY_AGAIN_WAITING", Map.of("sessionId", sp.getSessionId())));
        }
    }

    private void handleCancelPlayAgain(WebSocket socket, String playerId, JsonNode payload) throws Exception {
        SessionPayload sp = parse(payload, SessionPayload.class);
        if (sp.getSessionId() == null) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "sessionId is required"));
            return;
        }
        platform.cancelPlayAgain(sp.getSessionId(), playerId);
        send(socket, OutboundMessage.of("PLAY_AGAIN_CANCELLED", Map.of("sessionId", sp.getSessionId())));
    }

    // ── Friends ────────────────────────────────────────────────────────────────

    private void handleSendFriendRequest(WebSocket socket, String playerId, JsonNode payload) {
        if (payload == null || !payload.has("toUsername")) {
            send(socket, OutboundMessage.error("MISSING_FIELD", "toUsername is required"));
            return;
        }
        String toUsername = payload.get("toUsername").asText();

        // Temporarily set currentUser context so PlatformCore.sendFriendRequest() works
        // (PlatformCore is single-user in the GUI model; server re-uses it multi-user)
        // NOTE: this is a known limitation — friend requests route through the active session.
        // A full multi-user refactor would inject playerId into sendFriendRequest().
        String error = platform.sendFriendRequest(toUsername);
        if (error != null) {
            send(socket, OutboundMessage.error("FRIEND_REQUEST_FAILED", error));
        } else {
            send(socket, OutboundMessage.of("FRIEND_REQUEST_SENT", Map.of("to", toUsername)));
            // Push a notification to the recipient if they're connected
            registry.pushTo(toUsername, OutboundMessage.notification("Friend request from " + playerId));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void send(WebSocket socket, OutboundMessage msg) {
        if (socket == null || !socket.isOpen()) return;
        try {
            socket.send(mapper.writeValueAsString(msg));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to send frame", e);
        }
    }

    private <T> T parse(JsonNode node, Class<T> type) throws Exception {
        if (node == null) return type.getDeclaredConstructor().newInstance();
        return mapper.treeToValue(node, type);
    }

    /**
     * Deserialize the raw moveData JSON into the correct move POJO based on gameType.
     * Returns null if the gameType is unrecognised or deserialization fails.
     */
    private Object deserializeMove(String gameType, JsonNode moveData) {
        try {
            return switch (gameType) {
                case "TicTacToe"   -> {
                    int row   = moveData.get("row").asInt();
                    int col   = moveData.get("col").asInt();
                    String pc = moveData.has("piece") ? moveData.get("piece").asText() : "X";
                    yield new TicTacToeMove(row, col, Piece.valueOf(pc));
                }
                case "ConnectFour" -> {
                    String player = moveData.has("player") ? moveData.get("player").asText() : gameType;
                    int column = moveData.get("column").asInt();
                    yield new ConnectFourMove(player, column);
                }
                case "Checkers"    -> {
                    JsonNode targetNode = moveData.get("target");
                    CheckersMove.Coordinate target = new CheckersMove.Coordinate(
                            targetNode.get("x").asInt(), targetNode.get("y").asInt());
                    List<CheckersMove.Coordinate> coords = new ArrayList<>();
                    for (JsonNode c : moveData.get("moves"))
                        coords.add(new CheckersMove.Coordinate(c.get("x").asInt(), c.get("y").asInt()));
                    yield new CheckersMove(target, coords);
                }
                default            -> null;
            };
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not deserialize move for gameType=" + gameType, e);
            return null;
        }
    }
}
