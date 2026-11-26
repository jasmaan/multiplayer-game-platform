package ca.ucalgary.seng300.session;

import java.util.ArrayList;
import java.util.List;

import ca.ucalgary.seng300.core.GameStatus;

/**
 * Represents the current state of a game in progress.
 * Tracks the board, turn order, move history, and active player.
 */
public class GameState {

    // Immutable session and game info
    private final String sessionId;
    private final String gameType;
    private GameStatus status;
    private String activePlayerId;
    private final List<String> playerOrder;
    private int currentTurnIndex;
    private Object board;
    private final List<Object> moveHistory;

    /**
     * Initialize a new GameState for a session with the given players and game type.
     * @param sessionId the unique ID of the game session
     * @param gameType the type of game being played (e.g., "TicTacToe")
     * @param players the list of player IDs in turn order
     */
    public GameState(String sessionId, String gameType, List<String> players) {
        if(players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players list cannot be empty");
        }

        if(players.size() < 2) {
            throw new IllegalArgumentException("At least 2 players are required for a game");
        }

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }

        if (gameType == null || gameType.isBlank()) {
            throw new IllegalArgumentException("gameType cannot be null or blank");
        }

        this.sessionId = sessionId;
        this.gameType = gameType;
        this.status = GameStatus.STARTING;
        this.playerOrder = new ArrayList<>(players);
        this.currentTurnIndex = 0;
        this.activePlayerId = players.get(0);
        this.moveHistory = new ArrayList<>();
    }

    /**
     * Advance to the next player's turn in the order.
     * Wraps around to the first player after the last one.
     */
    public void advanceTurn() {
        currentTurnIndex = (currentTurnIndex + 1) % playerOrder.size();
        activePlayerId = playerOrder.get(currentTurnIndex);
    }

    // Getters and setters for mutable state
    // Player
    public String getActivePlayerId() { return activePlayerId; }
    public void setActivePlayerId(String activePlayerId) {
        int index = playerOrder.indexOf(activePlayerId);
        if (index == -1) {
            throw new IllegalArgumentException("Player not in the player order");
        }
        this.currentTurnIndex = index;
        this.activePlayerId = activePlayerId;
    }

    // Board    
    public Object getBoard() { return board; }
    public void setBoard(Object board) {
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        this.board = board;
    }

    // Move History
    public void addMoveToHistory(Object move) {
        if (move == null) {
            throw new IllegalArgumentException("Move cannot be null");
        }
        moveHistory.add(move);
    }
    public List<Object> getMoveHistory() { return new ArrayList<>(moveHistory); }

    // Session and game info
    public String getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }

    // Status
    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }

    // Player order info
    public List<String> getPlayerOrder() { return new ArrayList<>(playerOrder); }
    public int getCurrentTurnIndex() { return currentTurnIndex; }
}
