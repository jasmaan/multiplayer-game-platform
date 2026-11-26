package ca.ucalgary.seng300.server;

import ca.ucalgary.seng300.events.ChatMessageEvent;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.notification.NotificationService;
import ca.ucalgary.seng300.server.dto.OutboundMessage;
import ca.ucalgary.seng300.session.GameSession;
import ca.ucalgary.seng300.session.GameSessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Server-side implementation of NotificationListener.
 *
 * Bridges the single-listener GUI pattern to per-player WebSocket pushes.
 * Registered as the sole listener on the server's NotificationService so that
 * all platform notifications are forwarded over the wire instead of to a GUI.
 */
public class NotificationAdapter implements NotificationService.NotificationListener {

    private static final Logger LOG = Logger.getLogger(NotificationAdapter.class.getName());

    private final SessionRegistry    registry;
    private final GameSessionManager sessionManager;

    public NotificationAdapter(SessionRegistry registry, GameSessionManager sessionManager) {
        this.registry       = registry;
        this.sessionManager = sessionManager;
    }

    /**
     * Generic notification (turn alerts, game-over, move-rejected, room events).
     * Pushes a NOTIFICATION frame to the named player's socket.
     */
    @Override
    public void onNotification(String playerId, String message) {
        registry.pushTo(playerId, OutboundMessage.notification(message));
    }

    /**
     * Match-found: register a pending match and push MATCH_FOUND (with a matchId, no sessionId yet).
     * The session is only started once both players send ACCEPT_MATCH — handled in MessageRouter.
     */
    @Override
    public void onMatchFound(MatchFoundEvent event) {
        String matchId = UUID.randomUUID().toString();
        SessionRegistry.PendingMatch pm = new SessionRegistry.PendingMatch(
                matchId, event.getGameType(), event.getRoomId(), event.getPlayers());
        registry.addPendingMatch(pm);

        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId",  matchId);
        payload.put("roomId",   event.getRoomId());
        payload.put("gameType", event.getGameType());
        payload.put("players",  event.getPlayers());
        registry.pushToAll(event.getPlayers(), OutboundMessage.of("MATCH_FOUND", payload));
    }

    /**
     * Chat: push a CHAT_MESSAGE frame to every player in the session.
     * This is used instead of PlatformCore's chatCallbacks (which wrap in Platform.runLater
     * and are GUI-only). MessageRouter.handleChat() must NOT set a chatCallback.
     */
    @Override
    public void onChatMessage(ChatMessageEvent event) {
        GameSession session = sessionManager.getSession(event.getSessionId());
        if (session == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", event.getSessionId());
        payload.put("from",      event.getSenderId());
        payload.put("text",      event.getText());
        OutboundMessage frame = OutboundMessage.of("CHAT_MESSAGE", payload);
        registry.pushToAll(session.getPlayers(), frame);
    }

    /**
     * The NotificationListener interface provides only the sender's username, not
     * the recipient's. Friend-request pushes are handled directly in MessageRouter
     * after PlatformCore.sendFriendRequest() returns, where both IDs are known.
     */
    @Override
    public void onFriendRequestReceived(String fromUsername) {
        // no-op — see MessageRouter.handleSendFriendRequest()
    }
}
