package ca.ucalgary.seng300.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ca.ucalgary.seng300.core.GameStatus;
import ca.ucalgary.seng300.core.PlatformError;
import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.events.GameStateUpdate;
import ca.ucalgary.seng300.events.MoveRejectedEvent;
import ca.ucalgary.seng300.validation.Validation;

/**
 * Manages active game sessions.
 * Owns the move pipeline: receive move -> validate -> apply -> check end -> emit event.
 */
public class GameSessionManager {


    private final Map<String, GameSession> activeSessions;
    private final Map<String, GameEngine> engineRegistry;
    private final EventBus eventBus;

    /**
     * Initialize the session manager with empty registries.
     */
    public GameSessionManager(EventBus eventBus) {
        this.activeSessions = new HashMap<>();
        this.engineRegistry = new HashMap<>();
        this.eventBus = eventBus;
    }

    /**
     * Register a GameEngine implementation for a game type.
     * @param gameType the game identifier (e.g., "TicTacToe", "ConnectFour")
     * @param engine the engine implementation
     * @return true if registration is successful, false otherwise
     */
    public boolean registerEngine(String gameType, GameEngine engine) {
        if (gameType == null || gameType.isBlank() || engine == null){
            return false;
        }
        if (engineRegistry.containsKey(gameType)) {
            return false;
        }
        engineRegistry.put(gameType,engine);
        return true;
    }

    /**
     * Check if an engine is registered for a game type.
     * @param gameType the game identifier
     * @return true if an engine is registered, false otherwise
     */
    public boolean isEngineRegistered(String gameType) {
        return engineRegistry.containsKey(gameType);
    }

    /**
     * Get the registered GameEngine for a game type.
     * @param gameType the game identifier
     * @return the engine, or null if not registered
     */
    public GameEngine getEngine(String gameType) {
        return engineRegistry.get(gameType);
    }

    /**
     * Get a list of all registered game types.
     * @return list of game type identifiers
     */
    public List<String> getRegisteredGameTypes() {
        return new ArrayList<>(engineRegistry.keySet());
    }

    /**
     * Start a new game session.
     * @param roomId the room this session is linked to
     * @param gameType the type of game
     * @param players list of player IDs
     * @return the new session ID
     */
    public String startSession(String roomId, String gameType, List<String> players) {
        if (roomId == null || gameType == null || players == null || players.isEmpty()) {
            return null;
        }

        GameEngine engine = engineRegistry.get(gameType);
        if (engine == null) {
            return null; // No engine registered for this game type
        }

        String sessionId = UUID.randomUUID().toString();
        GameSession session = new GameSession(sessionId, roomId, gameType, players); // Creates a new game session

        // Initialize game state via engine
        Object initialBoard = engine.createInitialState(players); // Call createInitialState()
        if (initialBoard == null) {
            return null;
        }
        GameState state = session.getGameState();
        state.setBoard(initialBoard);
        state.setStatus(GameStatus.IN_PROGRESS);
        session.setStatus(GameStatus.IN_PROGRESS);

        activeSessions.put(sessionId, session);
        return sessionId;
    }

    /**
     * Submit a move for a player in a session. This is the main method that handles the move pipeline:
     * 1. Validate session and player
     * 2. Validate move via GameEngine
     * 3. Apply move and update state
     * 4. Check for end conditions
     * 5. Emit GameStateUpdate event or MoveRejectedEvent as appropriate
     * @param sessionId the session the move is for
     * @param playerId the player making the move
     * @param move the move object (game-specific format)
     * @return a MoveSubmissionResult indicating success or failure and containing the appropriate event data
     */
    public MoveSubmissionResult submitMove(String sessionId, String playerId, Object move) {
        // 1. Validate session and player
        GameSession session = activeSessions.get(sessionId);
        if (session == null) {
            return reject(sessionId, playerId, PlatformError.SESSION_NOT_FOUND, "No active session exists for this sessionId");
        }

        if (session.getStatus() == GameStatus.GAME_OVER) {
            return reject(sessionId, playerId, PlatformError.GAME_ALREADY_OVER, "This game session is already over");
        }


        GameState state = session.getGameState();
        if (state == null){
            return reject(sessionId, playerId, PlatformError.INVALID_STATE, "Game state is missing");
        }


        GameEngine engine = engineRegistry.get(session.getGameType());
        if  (engine == null) {
            return reject(sessionId, playerId, PlatformError.ENGINE_NOT_REGISTERED, "No engine is registered for the game type " + session.getGameType());
        }

        if (playerId == null || !session.getPlayers().contains(playerId)) {
            return reject (sessionId, playerId, PlatformError.UNKNOWN_PLAYER, "Player is not part of this session");
        }

        if (state.getActivePlayerId() == null) {
            return reject(sessionId, playerId, PlatformError.INVALID_STATE, "No active player is set for this session");
        }

        if (!state.getActivePlayerId().equals(playerId)) {
            return reject(sessionId, playerId, PlatformError.NOT_YOUR_TURN, "It is not your turn");
        }

        if (move == null) {
            return reject(sessionId, playerId, PlatformError.INVALID_MOVE, "Move cannot be null");
        }

        Validation validation = engine.validateMove(state.getBoard(), move);

        // 2. Validate move
        if (!validation.isValid()) {
            return reject(sessionId, playerId, PlatformError.INVALID_MOVE, validation.getMessage());
        }

        // 3. Apply move
        String previousPlayer = state.getActivePlayerId();
        Object newBoard = engine.applyMove(state.getBoard(), move);
        if (newBoard == null) {
            return reject(sessionId, playerId, PlatformError.INVALID_STATE, "Engine returned null board after move");
        }
        state.setBoard(newBoard);
        state.addMoveToHistory(move);

        // 4. Check end conditions
        boolean gameOver = engine.evaluateEnd(newBoard);
        String winnerId = null;

        if (gameOver) {
            winnerId = engine.getWinner(newBoard);
            state.setStatus(GameStatus.GAME_OVER);
            session.setStatus(GameStatus.GAME_OVER);
            session.setEndTime(System.currentTimeMillis());
        } else {
            state.advanceTurn();
        }

        // 5. Build and return GameStateUpdate
        GameStateUpdate update = new GameStateUpdate(
                UUID.randomUUID().toString(),
                sessionId,
                session.getPlayers(),
                gameOver ? GameStatus.GAME_OVER : GameStatus.IN_PROGRESS,
                state.getActivePlayerId(),
                previousPlayer,
                newBoard,
                gameOver ? "GAME_OVER" : "MOVE_ACCEPTED",
                gameOver,
                winnerId
        );

        return MoveSubmissionResult.accepted(update);
    }

    /**
     * End a game session, marking it as over and removing it from active sessions.
     * @param sessionId the session to end
     */
    public void endSession(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setStatus(GameStatus.GAME_OVER);
            session.setEndTime(System.currentTimeMillis());
            activeSessions.remove(sessionId);
        }
    }

    /**
    * Get a game session by its ID.
    * @param sessionId the session identifier
    * @return the GameSession, or null if not found
    */
    public GameSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * Get all active sessions for a given player.
     * @param playerId the player to query
     * @return list of active GameSessions the player is part of
     */
    public List<GameSession> getActiveSessionsForPlayer(String playerId) {
        List<GameSession> result = new ArrayList<>();
        for (GameSession session : activeSessions.values()) {
            if (session.getPlayers().contains(playerId)
                    && session.getStatus() != GameStatus.GAME_OVER) {
                result.add(session);
            }
        }
        return result;
    }

    /**
     * Check if it's the given player's turn in the specified session.
     * @param sessionId the session to check
     * @param playerId the player to check
     * @return true if it's the player's turn, false otherwise (including if session is invalid or over)
     */
    public boolean isPlayerTurn(String sessionId, String playerId) {
        GameSession session = activeSessions.get(sessionId);
        return session != null && session.getStatus() != GameStatus.GAME_OVER && session.getActivePlayerId() != null && session.getActivePlayerId().equals(playerId);
    }

    /**
     * Helper method to create a MoveSubmissionResult for a rejected move.
     * @param sessionId the session the move was for
     * @param playerId the player who made the move
     * @param reason the reason for rejection
     * @param message additional details about the rejection
     * @return a MoveSubmissionResult containing a MoveRejectedEvent
     */
    private MoveSubmissionResult reject(String sessionId, String playerId, PlatformError reason, String message) {
        MoveRejectedEvent event = new MoveRejectedEvent(
                UUID.randomUUID().toString(),
                sessionId,
                playerId,
                reason,
                message
        );
        return MoveSubmissionResult.rejected(event);
    }
}
