package ca.ucalgary.seng300.session;

import java.util.ArrayList;
import java.util.List;

import ca.ucalgary.seng300.core.GameStatus;

/**
 * Represents an active game session.
 * Contains the current game state, player information, and session metadata.
 */
public class GameSession {

    private final String sessionId;
    private final String roomId;
    private final String gameType;
    private final List<String> players;
    private final GameState gameState;
    private GameStatus status;
    private final long startTime;
    private long endTime;

    /**
     * Initialize a new GameSession with the given parameters.
     * @param sessionId the unique ID of the game session
     * @param roomId the ID of the room hosting the session
     * @param gameType the type of game being played (e.g., "TicTacToe")
     * @param players the list of player IDs in turn order
     */
    public GameSession(String sessionId, String roomId, String gameType, List<String> players) {
        if(sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }

        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId cannot be null or blank");
        }

        if(gameType == null || gameType.isBlank()) {
            throw new IllegalArgumentException("gameType cannot be null or blank");
        }

        if(players == null || players.isEmpty()) {
            throw new IllegalArgumentException("players cannot be null or empty");
        }

        this.sessionId = sessionId;
        this.roomId = roomId;
        this.gameType = gameType;
        this.players = new ArrayList<>(players);
        this.gameState = new GameState(sessionId, gameType, players);
        this.status = GameStatus.STARTING;
        this.startTime = System.currentTimeMillis();
        this.endTime = -1;
    }

    // Getters and setters for session metadata and state
    public String getSessionId() { return sessionId; }
    public String getRoomId() { return roomId; }
    public String getGameType() { return gameType; }
    public List<String> getPlayers() { return new ArrayList<>(players); }

    public GameState getGameState() { return gameState; }

    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        this.status = status;
    }

    public String getActivePlayerId() { return gameState.getActivePlayerId(); }

    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) {
        if (endTime < -1) {
            throw new IllegalArgumentException("endTime cannot be less than -1");
        }
        this.endTime = endTime;
    }
}
