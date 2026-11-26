package ca.ucalgary.seng300.core;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.events.GameStateUpdate;
import ca.ucalgary.seng300.session.GameEngine;
import ca.ucalgary.seng300.session.GameSession;
import ca.ucalgary.seng300.session.GameSessionManager;
import ca.ucalgary.seng300.session.MoveSubmissionResult;
import ca.ucalgary.seng300.validation.Validation;
import ca.ucalgary.seng300.validation.ValidationError;

public class GameSessionManagerTest {

    @Test
    void startSession_createsSession_whenEngineRegistered(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        assertNotNull(sessionId);

        GameSession session = mr.getSession(sessionId);
        assertNotNull(session);
        assertEquals(GameStatus.IN_PROGRESS, session.getStatus());
        assertEquals("Alice", session.getGameState().getActivePlayerId());
        assertNotNull(session.getGameState().getBoard());
        assertEquals(GameStatus.IN_PROGRESS, session.getGameState().getStatus());
    }

    @Test
    void startSession_returnsNull_whenEngineMissing(){
        GameSessionManager mr = new GameSessionManager(new EventBus());

        String sessionId = mr.startSession("room1", "MissingGame", List.of("Alice", "Bob"));

        assertNull(sessionId);
    }

    @Test
    void startSession_returnsNull_whenRoomIdIsNull() {
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession(null, "TestGame", List.of("Alice", "Bob"));

        assertNull(sessionId);
    }

    @Test
    void startSession_returnsNull_whenGameTypeIsNull() {
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", null, List.of("Alice", "Bob"));

        assertNull(sessionId);
    }

    @Test
    void startSession_returnsNull_whenPlayersIsEmpty() {
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of());

        assertNull(sessionId);
    }

    @Test
    void submitMove_acceptsValidMove(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        TestMove move = new TestMove("Alice", true, false);
        MoveSubmissionResult result = mr.submitMove(sessionId, "Alice", move);

        assertNotNull(result);
        assertTrue(result.isAccepted());
        assertNull(result.getRejection());

        GameStateUpdate update = result.getUpdate();
        assertNotNull(update);
        assertEquals("Alice", update.getPreviousPlayerId());
        assertEquals("Bob", update.getActivePlayerId());
        assertNotNull(update.isGameOver());
        assertEquals(GameStatus.IN_PROGRESS, update.getStatus());
    }

    @Test
    void submitMove_rejectsWhenNotPlayersTurn(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        TestMove move = new TestMove("Bob", true, false);
        MoveSubmissionResult result = mr.submitMove(sessionId, "Bob", move);

        assertNotNull(result);
        assertFalse(result.isAccepted());
        assertNull(result.getUpdate());
        assertNotNull(result.getRejection());
        assertEquals(PlatformError.NOT_YOUR_TURN, result.getRejection().getReason());
    }

    @Test
    void submitMove_rejects_whenSessionNotFound() {
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        TestMove move = new TestMove("Alice", true, false);
        MoveSubmissionResult result = mr.submitMove("nonexistent-session", "Alice", move);

        assertNotNull(result);
        assertFalse(result.isAccepted());
        assertNull(result.getUpdate());
        assertNotNull(result.getRejection());
        assertEquals(PlatformError.SESSION_NOT_FOUND, result.getRejection().getReason());
    }

    @Test
    void submitMove_rejects_whenSessionAlreadyGameOver() {
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        // Simulate game over
        TestMove winningMove = new TestMove("Alice", true, true);
        MoveSubmissionResult result1 = mr.submitMove(sessionId, "Alice", winningMove);
        assertNotNull(result1);
        assertTrue(result1.isAccepted());
        assertNull(result1.getRejection());

        // Attempt to submit move after game over
        TestMove moveAfterGameOver = new TestMove("Bob", true, false);
        MoveSubmissionResult result2 = mr.submitMove(sessionId, "Bob", moveAfterGameOver);

        assertNotNull(result2);
        assertFalse(result2.isAccepted());
        assertNull(result2.getUpdate());
        assertNotNull(result2.getRejection());
        assertEquals(PlatformError.GAME_ALREADY_OVER, result2.getRejection().getReason());
    }

    @Test
    void submitMove_rejectsInvalidMove(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        TestMove move = new TestMove("Alice", false, false);
        MoveSubmissionResult result = mr.submitMove(sessionId, "Alice", move);

        assertNotNull(result);
        assertFalse(result.isAccepted());
        assertNull(result.getUpdate());
        assertNotNull(result.getRejection());
        assertEquals(PlatformError.INVALID_MOVE, result.getRejection().getReason());
    }

    @Test
    void submitMove_setsGameOver_whenEngineGameEnded(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        TestMove move = new TestMove("Alice", true, true);
        MoveSubmissionResult result = mr.submitMove(sessionId, "Alice", move);

        assertNotNull(result);
        assertTrue(result.isAccepted());
        assertNull(result.getRejection());

        GameStateUpdate update = result.getUpdate();
        assertNotNull(update);
        assertTrue(update.isGameOver());
        assertEquals("Alice", update.getWinnerId());
        assertEquals(GameStatus.GAME_OVER, update.getStatus());

        GameSession session = mr.getSession(sessionId);
        assertEquals(GameStatus.GAME_OVER, session.getStatus());
        assertEquals(GameStatus.GAME_OVER, session.getGameState().getStatus());
    }

    @Test
    void submitMove_rejects_whenPlayerNotInSession(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());
        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        TestMove move = new TestMove("Charlie", true, false);
        MoveSubmissionResult result = mr.submitMove(sessionId,"Charlie", move);

        assertNotNull(result);
        assertFalse(result.isAccepted());
        assertNull(result.getUpdate());
        assertNotNull(result.getRejection());
        assertEquals(PlatformError.UNKNOWN_PLAYER, result.getRejection().getReason());
    }

    @Test
    void submitMove_rejects_whenPlayerIdIsNull(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        TestMove move = new TestMove("Alice", true, false);
        MoveSubmissionResult result = mr.submitMove(sessionId, null, move);

        assertNotNull(result);
        assertFalse(result.isAccepted());
        assertNull(result.getUpdate());
        assertNotNull(result.getRejection());
        assertEquals(PlatformError.UNKNOWN_PLAYER, result.getRejection().getReason());
    }

    @Test
    void submitMove_rejects_whenMoveIsNull(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        MoveSubmissionResult result = mr.submitMove(sessionId, "Alice", null);

        assertNotNull(result);
        assertFalse(result.isAccepted());
        assertNull(result.getUpdate());
        assertNotNull(result.getRejection());
        assertEquals(PlatformError.INVALID_MOVE, result.getRejection().getReason());
    }

    @Test
    void registerEngine_returnsFalse_whenDuplicate(){
        GameSessionManager mr = new GameSessionManager(new EventBus());

        boolean first = mr.registerEngine("TestGame", new FakeGameEngine());
        boolean second = mr.registerEngine("TestGame", new FakeGameEngine());

        assertTrue(first);
        assertFalse(second);
    }

    @Test
    void registerEngine_returnsFalse_whenGameTypeBlank(){
        GameSessionManager mr = new GameSessionManager(new EventBus());

        boolean result = mr.registerEngine("  ", new FakeGameEngine());

        assertFalse(result);
    }

    @Test
    void registerEngine_returnsFalse_whenEngineIsNull(){
        GameSessionManager mr = new GameSessionManager(new EventBus());

        boolean result = mr.registerEngine("TestGame", null);

        assertFalse(result);
    }

    @Test
    void isPlayerTurn_returnsTrueForActivePlayer_andFalseForOther(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        assertTrue(mr.isPlayerTurn(sessionId, "Alice"));
        assertFalse(mr.isPlayerTurn(sessionId, "Bob"));
    }

    @Test
    void isPlayerTurn_switchesAfterValidMove(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room", "TestGame", List.of("Alice", "Bob"));

        MoveSubmissionResult result = mr.submitMove(sessionId, "Alice", new TestMove("Alice", true, false));
        assertTrue(result.isAccepted());

        assertFalse(mr.isPlayerTurn(sessionId, "Alice"));
        assertTrue(mr.isPlayerTurn(sessionId, "Bob"));
    }

    @Test
    void isPlayerTurn_returnsFalse_whenGameOver(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        MoveSubmissionResult result = mr.submitMove(sessionId, "Alice", new TestMove("Alice", true, true));

        assertFalse(mr.isPlayerTurn(sessionId, "Alice"));
        assertFalse(mr.isPlayerTurn(sessionId, "Bob"));
    }

    @Test
    void getActiveSessionForPlayer_returnsOnlyNonGameOverSessions(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String activeSessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));
        String finishedSessionId = mr.startSession("room2", "TestGame", List.of("Alice", "Charlie"));

        MoveSubmissionResult result = mr.submitMove(finishedSessionId, "Alice", new TestMove("Alice", true, true));
        assertTrue(result.isAccepted());

        List<GameSession> sessions = mr.getActiveSessionsForPlayer("Alice");

        assertEquals(1, sessions.size());
        assertEquals(activeSessionId, sessions.get(0).getSessionId());
    }

    @Test
    void getActiveSessionsForPlayer_returnsEmptyList_whenPlayerHasNoSessions(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        List<GameSession> sessions = mr.getActiveSessionsForPlayer("Charlie");

        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void endSession_removesSession(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));

        assertNotNull(mr.getSession(sessionId));

        mr.endSession(sessionId);

        assertNull(mr.getSession(sessionId));
    }

    @Test
    void fullGameFlow_validMoveThenWinningMoveThenRejectAfterGameOver(){
        GameSessionManager mr = new GameSessionManager(new EventBus());
        mr.registerEngine("TestGame", new FakeGameEngine());

        String sessionId = mr.startSession("room1", "TestGame", List.of("Alice", "Bob"));
        assertNotNull(sessionId);

        MoveSubmissionResult firstMove = mr.submitMove(sessionId, "Alice", new TestMove("Alice", true, false));
        assertTrue(firstMove.isAccepted());
        assertEquals("Bob", firstMove.getUpdate().getActivePlayerId());

        MoveSubmissionResult winningMove = mr.submitMove(sessionId, "Bob", new TestMove("Bob", true, true));
        assertTrue(winningMove.isAccepted());
        assertTrue(winningMove.getUpdate().isGameOver());
        assertEquals("Bob", winningMove.getUpdate().getWinnerId());

        GameSession session = mr.getSession(sessionId);
        assertNotNull(session);
        assertEquals(GameStatus.GAME_OVER, session.getStatus());

        MoveSubmissionResult afterGameOver = mr.submitMove(sessionId, "Alice", new TestMove("Alice", true, false));
        assertFalse(afterGameOver.isAccepted());
        assertEquals(PlatformError.GAME_ALREADY_OVER, afterGameOver.getRejection().getReason());
    }

    /**
     * Fake GameEngine for testing purposes. Behavior is controlled by TestMove properties.
     */
    private static class FakeGameEngine implements GameEngine {
        @Override
        public Object createInitialState(List<String> playerIds) {
            return new TestBoard();
        }

        @Override
        public Validation validateMove(Object Board, Object move) {
            if (!(move instanceof TestMove)) {
                return new Validation(ValidationError.INVALID_FORMAT, "Invalid move object");
            }
            TestMove testMove = (TestMove) move;
            if (testMove.isValid()) {
                return new Validation(ValidationError.NONE, "Move is valid");
            } else {
                return new Validation(ValidationError.INVALID_MOVE, "Invalid move");
            }
        }

        @Override
        public Object applyMove(Object board, Object move) {
            TestBoard oldBoard = (TestBoard) board;
            TestMove testMove = (TestMove) move;

            TestBoard newBoard = new TestBoard();
            newBoard.moveCount = oldBoard.moveCount + 1;

            if (testMove.winningMove) {
                newBoard.winnerId = testMove.playerId;
            }

            return newBoard;
        }

        @Override
        public boolean evaluateEnd(Object board) {
            TestBoard testBoard = (TestBoard) board;
            return testBoard.winnerId != null;
        }

        @Override
        public String getWinner(Object board) {
            TestBoard testBoard = (TestBoard) board;
            return testBoard.winnerId;
        }
    }

    private static class TestBoard {
        int moveCount = 0;
        String winnerId = null;
    }

    private static class TestMove {
        String playerId;
        boolean valid;
        boolean winningMove;

        public TestMove(String playerId, boolean valid, boolean winningMove) {
            this.playerId = playerId;
            this.valid = valid;
            this.winningMove = winningMove;
        }

        public boolean isValid() {
            return valid;
        }
    }
}