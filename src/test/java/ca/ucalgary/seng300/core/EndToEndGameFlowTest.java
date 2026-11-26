package ca.ucalgary.seng300.core;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.session.GameEngine;
import ca.ucalgary.seng300.session.MoveSubmissionResult;
import ca.ucalgary.seng300.validation.Validation;
import ca.ucalgary.seng300.validation.ValidationError;

/**
 * End-to-end integration demo: login → queue → match → play → game over → dashboard.
 *
 * Exercises the complete platform flow through PlatformCore as a single chained sequence,
 * verifying context transitions and notifications at each step.
 *
 * Uses a FakeGameEngine so tests are deterministic and have no external dependencies.
 */
class EndToEndGameFlowTest {

    private PlatformCore platform;
    private String alice;
    private String bob;

    @BeforeEach
    void setUp() {
        platform = new PlatformCore();
        platform.getSessionManager().registerEngine("TestGame", new FakeGameEngine());

        alice = platform.registerPlayer("alice", "smith", "Password1!");
        bob   = platform.registerPlayer("bob",   "jones", "Password1!");
    }

    // ── Full path flow ─────────────────────────────────────────────────

    @Test
    void fullFlow_loginToGameOverToDashboard() {
        // 1. Login — both players land on DASHBOARD
        assertTrue(platform.login(alice, "Password1!"));
        assertTrue(platform.login(bob,   "Password1!"));
        assertEquals(PlayerContext.DASHBOARD, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.DASHBOARD, platform.getPlayerContext(bob));

        // 2. Join queue — both transition to IN_QUEUE
        platform.joinQueue(alice, "TestGame");
        platform.joinQueue(bob,   "TestGame");
        assertEquals(PlayerContext.IN_QUEUE, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.IN_QUEUE, platform.getPlayerContext(bob));

        // 3. Match found — both transition to IN_GAME
        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(bob));

        // 4. Game session starts
        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));
        assertNotNull(sessionId);

        // 5. Alice submits a winning move — processed through PlatformCore → EventBus
        MoveSubmissionResult result = platform.submitMove(sessionId, alice,
                new TestMove(alice, true, true));
        assertNotNull(result);
        assertTrue(result.isAccepted());
        assertTrue(result.getUpdate().isGameOver());
        assertEquals(alice, result.getUpdate().getWinnerId());

        // 6. Game over — both players transition to VIEWING_RESULTS
        assertEquals(PlayerContext.VIEWING_RESULTS, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.VIEWING_RESULTS, platform.getPlayerContext(bob));

        // 7. Return to dashboard — both land back on DASHBOARD
        platform.returnToDashboard(alice);
        platform.returnToDashboard(bob);
        assertEquals(PlayerContext.DASHBOARD, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.DASHBOARD, platform.getPlayerContext(bob));
    }

    // ── Notifications throughout the flow ───────────────────────────────────

    @Test
    void fullFlow_correctNotificationsDeliveredAtEachStep() {
        platform.login(alice, "Password1!");
        platform.login(bob,   "Password1!");
        platform.getNotificationService().clearNotificationLog();

        // Match found
        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));

        List<String> log = platform.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(alice) && e.contains("Match found")));
        assertTrue(log.stream().anyMatch(e -> e.contains(bob)   && e.contains("Match found")));

        // Game over
        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));
        platform.submitMove(sessionId, alice, new TestMove(alice, true, true));

        log = platform.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(alice) && e.contains("You won")));
        assertTrue(log.stream().anyMatch(e -> e.contains(bob)   && e.contains("You lost")));
    }

    // ── Multi-move game ──────────────────────────────────────────────────────

    @Test
    void fullFlow_multipleMovesBeforeGameOver() {
        platform.login(alice, "Password1!");
        platform.login(bob,   "Password1!");

        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));

        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));

        // Non-winning moves alternate: alice → bob → alice wins
        MoveSubmissionResult r1 = platform.submitMove(sessionId, alice,
                new TestMove(alice, true, false));
        assertTrue(r1.isAccepted());
        assertFalse(r1.getUpdate().isGameOver());
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(alice));

        MoveSubmissionResult r2 = platform.submitMove(sessionId, bob,
                new TestMove(bob, true, false));
        assertTrue(r2.isAccepted());
        assertFalse(r2.getUpdate().isGameOver());
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(bob));

        MoveSubmissionResult r3 = platform.submitMove(sessionId, alice,
                new TestMove(alice, true, true));
        assertTrue(r3.isAccepted());
        assertTrue(r3.getUpdate().isGameOver());

        assertEquals(PlayerContext.VIEWING_RESULTS, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.VIEWING_RESULTS, platform.getPlayerContext(bob));
    }

    // ── Invalid move during game does not change context ─────────────────────

    @Test
    void fullFlow_invalidMoveRejectedContextUnchanged() {
        platform.login(alice, "Password1!");
        platform.login(bob,   "Password1!");

        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));

        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));

        // Alice submits an invalid move
        MoveSubmissionResult result = platform.submitMove(sessionId, alice,
                new TestMove(alice, false, false));
        assertFalse(result.isAccepted());

        // Context stays IN_GAME — game is not over
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(bob));
    }

    // ── Logout during game removes context ───────────────────────────────────

    @Test
    void fullFlow_logoutMidGameRemovesPlayerContext() {
        platform.login(alice, "Password1!");
        platform.login(bob,   "Password1!");

        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));

        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(alice));

        platform.logout(alice);

        assertNull(platform.getPlayerContext(alice));
        assertFalse(platform.isPlayerLoggedIn(alice));
    }

    // ── Matching Test ───────────────────────────────────

    @Test
    void matchPlayerTriggersEvent() {
        // Log both players in
        platform.login(alice, "Password1!");
        platform.login(bob,   "Password1!");

        // Register a no-op listener so tryMatch() is allowed to fire from joinQueue().
        // Without a listener the guard in tryMatch() returns early (avoids interfering
        // with unrelated tests that don't want auto-matching).
        platform.getNotificationService().setListener((id, msg) -> {});
        platform.getNotificationService().clearNotificationLog();

        // First join: queued, waiting. Second join: tryMatch fires, auto-matches both.
        platform.joinQueue(alice, "Chess");
        platform.joinQueue(bob, "Chess");

        // Both players matched and removed from queue
        assertEquals(0, platform.getMatchMaking().getQueueSize("Chess"));
        assertFalse(platform.getMatchMaking().isPlayerInQueue(alice));
        assertFalse(platform.getMatchMaking().isPlayerInQueue(bob));

        // MatchFoundEvent reached PlatformCore — contexts updated to IN_GAME
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(alice));
        assertEquals(PlayerContext.IN_GAME, platform.getPlayerContext(bob));

        // Match found notification should have been sent
        List<String> log = platform.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(alice) && e.contains("Match found")));
        assertTrue(log.stream().anyMatch(e -> e.contains(bob)   && e.contains("Match found")));

        // Room was created with both players.
        // After tryMatch both players join the room so it becomes FULL — use getAllRooms,
        // not getAvailableRooms (which only returns OPEN rooms).
        assertFalse(platform.getRoomManager().getAllRooms("Chess").isEmpty());
        List<String> roomPlayers = platform.getRoomManager().getAllRooms("Chess").get(0).getPlayers();
        assertTrue(roomPlayers.contains(alice));
        assertTrue(roomPlayers.contains(bob));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
            TestBoard prev = (TestBoard) board;
            TestMove  tm   = (TestMove)  move;
            TestBoard next = new TestBoard();
            next.moveCount = prev.moveCount + 1;
            if (tm.winningMove) next.winnerId = tm.playerId;
            return next;
        }

        @Override
        public boolean evaluateEnd(Object board) {
            return ((TestBoard) board).winnerId != null;
        }

        @Override
        public String getWinner(Object board) {
            return ((TestBoard) board).winnerId;
        }
    }

    private static class TestBoard {
        int    moveCount = 0;
        String winnerId  = null;
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