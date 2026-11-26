package ca.ucalgary.seng300.events;

import ca.ucalgary.seng300.core.PlatformError;

/**
 * Event emitted when a player's move is rejected (invalid or not their turn).
 */
public class MoveRejectedEvent extends GameEvent {

    private final String sessionId;
    private final String playerId;
    private final PlatformError reason;
    private final String message;

    public MoveRejectedEvent(String eventId, String sessionId, String playerId,
                             PlatformError reason, String message) {
        super(eventId, "MoveRejected");
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.reason = reason;
        this.message = message;
    }

    public String getSessionId() { return sessionId; }
    public String getPlayerId() { return playerId; }
    public PlatformError getReason() { return reason; }
    public String getMessage() { return message; }
}
