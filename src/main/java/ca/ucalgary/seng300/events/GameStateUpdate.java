package ca.ucalgary.seng300.events;

import java.util.List;

import ca.ucalgary.seng300.core.GameStatus;

/**
 * Event emitted when game state changes.
 * Published by GameSessionManager, consumed by PlatformCore.
 * Contains all information needed to update players' views.
 */
public class GameStateUpdate extends GameEvent {

    private final String sessionId;
    private final List<String> recipients;
    private final GameStatus status;
    private final String activePlayerId;
    private final String previousPlayerId;
    private final Object payload;
    private final String moveResult;
    private final boolean gameOver;
    private final String winnerId;

    public GameStateUpdate(String eventId, String sessionId, List<String> recipients,
                           GameStatus status, String activePlayerId, String previousPlayerId,
                           Object payload, String moveResult, boolean gameOver, String winnerId) {
        super(eventId, "GameStateUpdate");
        this.sessionId = sessionId;
        this.recipients = recipients;
        this.status = status;
        this.activePlayerId = activePlayerId;
        this.previousPlayerId = previousPlayerId;
        this.payload = payload;
        this.moveResult = moveResult;
        this.gameOver = gameOver;
        this.winnerId = winnerId;
    }

    public String getSessionId() { return sessionId; }
    public List<String> getRecipients() { return recipients; }
    public GameStatus getStatus() { return status; }
    public String getActivePlayerId() { return activePlayerId; }
    public String getPreviousPlayerId() { return previousPlayerId; }
    public Object getPayload() { return payload; }
    public String getMoveResult() { return moveResult; }
    public boolean isGameOver() { return gameOver; }
    public String getWinnerId() { return winnerId; }
}
