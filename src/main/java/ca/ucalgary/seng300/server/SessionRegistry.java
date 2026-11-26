package ca.ucalgary.seng300.server;

import ca.ucalgary.seng300.server.dto.OutboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe registry mapping authenticated playerId ↔ WebSocket connection.
 *
 * Also holds the per-connection rate-limiter state and the last-accepted
 * moveId cache for idempotency checks.
 */
public class SessionRegistry {

    private static final Logger LOG = Logger.getLogger(SessionRegistry.class.getName());

    // ── Rate-limit constants ───────────────────────────────────────
    private static final int  RATE_WINDOW_MS  = 5_000;
    private static final int  RATE_MAX_MSGS   = 20;

    private final ObjectMapper mapper;

    // playerId → WebSocket
    private final Map<String, WebSocket> playerToSocket = new ConcurrentHashMap<>();
    // WebSocket → playerId  (reverse lookup on close/error)
    private final Map<WebSocket, String> socketToPlayer = new ConcurrentHashMap<>();

    // playerId → message count in current window
    private final Map<String, int[]>  rateCount      = new ConcurrentHashMap<>();
    // playerId → window start timestamp
    private final Map<String, long[]> rateWindowStart = new ConcurrentHashMap<>();

    // sessionId → last accepted moveId (idempotency)
    private final Map<String, String> lastMoveId = new ConcurrentHashMap<>();

    // matchId → pending match awaiting player acceptance
    private final Map<String, PendingMatch> pendingMatches = new ConcurrentHashMap<>();

    // ── Pending match (pre-session accept/decline handshake) ───────
    public static class PendingMatch {
        public final String       matchId;
        public final String       gameType;
        public final String       roomId;
        public final List<String> players;
        public final Set<String>  accepted = ConcurrentHashMap.newKeySet();

        public PendingMatch(String matchId, String gameType, String roomId, List<String> players) {
            this.matchId  = matchId;
            this.gameType = gameType;
            this.roomId   = roomId;
            this.players  = players;
        }
    }

    public SessionRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // ── Registration ───────────────────────────────────────────────

    public void register(String playerId, WebSocket socket) {
        playerToSocket.put(playerId, socket);
        socketToPlayer.put(socket, playerId);
    }

    public void unregister(WebSocket socket) {
        String playerId = socketToPlayer.remove(socket);
        if (playerId != null) {
            playerToSocket.remove(playerId);
            rateCount.remove(playerId);
            rateWindowStart.remove(playerId);
        }
    }

    public String getPlayerId(WebSocket socket) {
        return socketToPlayer.get(socket);
    }

    public boolean isRegistered(WebSocket socket) {
        return socketToPlayer.containsKey(socket);
    }

    // ── Push ───────────────────────────────────────────────────────

    /** Push a typed message to one player. No-op if the player is offline. */
    public void pushTo(String playerId, OutboundMessage msg) {
        WebSocket socket = playerToSocket.get(playerId);
        if (socket == null || !socket.isOpen()) return;
        try {
            socket.send(mapper.writeValueAsString(msg));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "pushTo failed for " + playerId, e);
        }
    }

    /** Push a typed message to every connected player. */
    public void broadcast(OutboundMessage msg) {
        playerToSocket.keySet().forEach(id -> pushTo(id, msg));
    }

    /** Push to a specific set of playerIds (e.g. all players in a session). */
    public void pushToAll(Collection<String> playerIds, OutboundMessage msg) {
        playerIds.forEach(id -> pushTo(id, msg));
    }

    // ── Rate limiting ──────────────────────────────────────────────

    /**
     * Returns true if the player is within the allowed rate window.
     * Call before processing any inbound frame.
     */
    public boolean checkRateLimit(String playerId) {
        long now = System.currentTimeMillis();
        long[] winStart = rateWindowStart.computeIfAbsent(playerId, k -> new long[]{now});
        int[]  count    = rateCount.computeIfAbsent(playerId, k -> new int[]{0});

        if (now - winStart[0] > RATE_WINDOW_MS) {
            // reset window
            winStart[0] = now;
            count[0]    = 0;
        }
        count[0]++;
        return count[0] <= RATE_MAX_MSGS;
    }

    // ── Move idempotency ───────────────────────────────────────────

    /** Returns true if this moveId was already applied to the session. */
    public boolean isDuplicateMove(String sessionId, String moveId) {
        return moveId != null && moveId.equals(lastMoveId.get(sessionId));
    }

    /** Record the accepted moveId for a session. */
    public void recordMoveId(String sessionId, String moveId) {
        if (moveId != null) lastMoveId.put(sessionId, moveId);
    }

    // ── Pending match management ───────────────────────────────────

    public void addPendingMatch(PendingMatch pm) {
        pendingMatches.put(pm.matchId, pm);
    }

    public PendingMatch getPendingMatch(String matchId) {
        return pendingMatches.get(matchId);
    }

    /** Atomically removes and returns the pending match, or null if already removed. */
    public PendingMatch removePendingMatch(String matchId) {
        return pendingMatches.remove(matchId);
    }
}
