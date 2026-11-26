package ca.ucalgary.seng300.core;

import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.events.GameStateUpdate;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.events.MoveRejectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end event flow tests.
 *
 * Verifies that events published to the EventBus reach PlatformCore's handlers
 * and produce the correct side effects (context transitions + notification log entries).
 *
 * Strategy: publish events directly through core.getEventBus() so we bypass
 * no layers — the full subscribe → publish → handle → notify pipeline runs.
 */
class EventFlowTest {

    private PlatformCore core;
    private EventBus bus;
    private String player1;
    private String player2;

    @BeforeEach
    void setUp() {
        core = new PlatformCore();
        bus  = core.getEventBus();

        // Register and log in two players so they have active contexts
        player1 = core.registerPlayer("alice", "smith", "Password1!");
        player2 = core.registerPlayer("bob",   "jones", "Password1!");
        core.login(player1, "Password1!");
        core.login(player2, "Password1!");

        // Clear any notifications produced during login/register
        core.getNotificationService().clearNotificationLog();
    }

    // ── MatchFoundEvent ──────────────────────────────────────────────────────

    @Test
    void matchFoundEvent_published_transitionsBothPlayersToInGame() {
        MatchFoundEvent event = new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(player1, player2), "tictactoe", "room-1");

        bus.publish(event);

        assertEquals(PlayerContext.IN_GAME, core.getPlayerContext(player1));
        assertEquals(PlayerContext.IN_GAME, core.getPlayerContext(player2));
    }

    @Test
    void matchFoundEvent_published_notifiesBothPlayers() {
        MatchFoundEvent event = new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(player1, player2), "tictactoe", "room-1");

        bus.publish(event);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("Match found")));
        assertTrue(log.stream().anyMatch(e -> e.contains(player2) && e.contains("Match found")));
    }

    // ── GameStateUpdate — mid-game ───────────────────────────────────────────

    @Test
    void gameStateUpdate_activePlayerInGame_receivesGameUpdatedNotification() {
        // Put both players in IN_GAME context first
        core.setPlayerContext(player1, PlayerContext.IN_GAME);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        // player1 is the active player
        GameStateUpdate update = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.IN_PROGRESS, player1, player2, null, "MOVE_ACCEPTED", false, null);

        bus.publish(update);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("Game updated")));
    }

    @Test
    void gameStateUpdate_activePlayerOnDashboard_receivesTurnAlert() {
        // player1 is on dashboard, not in the game view
        core.setPlayerContext(player1, PlayerContext.DASHBOARD);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        GameStateUpdate update = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.IN_PROGRESS, player1, player2, null, "MOVE_ACCEPTED", false, null);

        bus.publish(update);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("turn")));
    }

    @Test
    void gameStateUpdate_notActivePlayer_receivesNoTurnAlert() {
        core.setPlayerContext(player1, PlayerContext.DASHBOARD);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        // player2 is active — player1 should not get a turn alert
        GameStateUpdate update = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.IN_PROGRESS, player2, player1, null, "MOVE_ACCEPTED", false, null);

        bus.publish(update);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertFalse(log.stream().anyMatch(e -> e.contains(player1) && e.contains("turn")));
    }

    // ── GameStateUpdate — game over ──────────────────────────────────────────

    @Test
    void gameStateUpdate_gameOver_transitionsBothPlayersToViewingResults() {
        core.setPlayerContext(player1, PlayerContext.IN_GAME);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        GameStateUpdate gameOver = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.GAME_OVER, null, player2, null, "GAME_OVER", true, player1);

        bus.publish(gameOver);

        assertEquals(PlayerContext.VIEWING_RESULTS, core.getPlayerContext(player1));
        assertEquals(PlayerContext.VIEWING_RESULTS, core.getPlayerContext(player2));
    }

    @Test
    void gameStateUpdate_gameOver_notifiesWinnerAndLoser() {
        core.setPlayerContext(player1, PlayerContext.IN_GAME);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        // player1 wins
        GameStateUpdate gameOver = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.GAME_OVER, null, player2, null, "GAME_OVER", true, player1);

        bus.publish(gameOver);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("You won")));
        assertTrue(log.stream().anyMatch(e -> e.contains(player2) && e.contains("You lost")));
    }

    // ── MoveRejectedEvent ────────────────────────────────────────────────────

    @Test
    void moveRejectedEvent_published_notifiesRejectedPlayer() {
        MoveRejectedEvent rejection = new MoveRejectedEvent(
                UUID.randomUUID().toString(), "session-1", player1,
                PlatformError.INVALID_MOVE, "Move is not valid.");

        bus.publish(rejection);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("Move rejected")));
    }

    @Test
    void moveRejectedEvent_published_doesNotNotifyOtherPlayer() {
        MoveRejectedEvent rejection = new MoveRejectedEvent(
                UUID.randomUUID().toString(), "session-1", player1,
                PlatformError.INVALID_MOVE, "Move is not valid.");

        bus.publish(rejection);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertFalse(log.stream().anyMatch(e -> e.contains(player2) && e.contains("Move rejected")));
    }

    // ── Null safety ──────────────────────────────────────────────────────────

    @Test
    void eventBus_publishNull_doesNotThrow() {
        assertDoesNotThrow(() -> bus.publish(null));
    }

    @Test
    void eventBus_subscribeNullHandler_doesNotThrow() {
        assertDoesNotThrow(() -> bus.subscribe("GameStateUpdate", null));
    }

    @Test
    void eventBus_duplicateSubscription_handlerCalledOnce() {
        int[] callCount = {0};
        ca.ucalgary.seng300.events.EventHandler counter = e -> callCount[0]++;

        bus.subscribe("MatchFound", counter);
        bus.subscribe("MatchFound", counter); // duplicate — should be ignored

        MatchFoundEvent event = new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(player1), "tictactoe", "room-1");
        bus.publish(event);

        // counter handler called exactly once; PlatformCore's handler also fires but that's separate
        assertEquals(1, callCount[0]);
    }

    // ── Draw scenario ────────────────────────────────────────────────────────

    @Test
    void gameStateUpdate_gameOverDraw_notifiesBothPlayersWithDraw() {
        core.setPlayerContext(player1, PlayerContext.IN_GAME);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        // winnerId is null — indicates a draw
        GameStateUpdate draw = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.GAME_OVER, null, null, null, "GAME_OVER", true, null);

        bus.publish(draw);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("draw")));
        assertTrue(log.stream().anyMatch(e -> e.contains(player2) && e.contains("draw")));
    }

    @Test
    void gameStateUpdate_gameOverDraw_transitionsBothPlayersToViewingResults() {
        core.setPlayerContext(player1, PlayerContext.IN_GAME);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        GameStateUpdate draw = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.GAME_OVER, null, null, null, "GAME_OVER", true, null);

        bus.publish(draw);

        assertEquals(PlayerContext.VIEWING_RESULTS, core.getPlayerContext(player1));
        assertEquals(PlayerContext.VIEWING_RESULTS, core.getPlayerContext(player2));
    }

    // ── Turn alert — all non-IN_GAME contexts ────────────────────────────────

    @Test
    void gameStateUpdate_activePlayerInLobby_receivesTurnAlert() {
        // player1 is in a lobby waiting for another game to start
        core.setPlayerContext(player1, PlayerContext.IN_LOBBY);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        GameStateUpdate update = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.IN_PROGRESS, player1, player2, null, "MOVE_ACCEPTED", false, null);

        bus.publish(update);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("turn")));
    }

    @Test
    void gameStateUpdate_activePlayerInQueue_receivesTurnAlert() {
        // player1 is queued for another game while a different session is ongoing
        core.setPlayerContext(player1, PlayerContext.IN_QUEUE);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        GameStateUpdate update = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-1", List.of(player1, player2),
                GameStatus.IN_PROGRESS, player1, player2, null, "MOVE_ACCEPTED", false, null);

        bus.publish(update);

        List<String> log = core.getNotificationService().getNotificationLog();
        assertTrue(log.stream().anyMatch(e -> e.contains(player1) && e.contains("turn")));
    }

    // ── Game over while player is on VIEWING_RESULTS (concurrent sessions) ───

    @Test
    void gameStateUpdate_gameOverWhileViewingResults_playerNotifiedWithSessionId() {
        // player1 is already viewing results from a finished game
        // a second concurrent session just ended
        core.setPlayerContext(player1, PlayerContext.VIEWING_RESULTS);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        GameStateUpdate gameOver = new GameStateUpdate(
                UUID.randomUUID().toString(), "session-2", List.of(player1, player2),
                GameStatus.GAME_OVER, null, player2, null, "GAME_OVER", true, player1);

        bus.publish(gameOver);

        List<String> log = core.getNotificationService().getNotificationLog();
        // notification should reference the session so player knows which game ended
        assertTrue(log.stream().anyMatch(e -> e.contains(player1)
                && e.contains("session-2")
                && e.contains("You won")));
    }

    // ── MoveRejectedEvent — context unchanged ────────────────────────────────

    @Test
    void moveRejectedEvent_published_playerContextRemainsInGame() {
        core.setPlayerContext(player1, PlayerContext.IN_GAME);

        MoveRejectedEvent rejection = new MoveRejectedEvent(
                UUID.randomUUID().toString(), "session-1", player1,
                PlatformError.INVALID_MOVE, "Move is not valid.");

        bus.publish(rejection);

        // rejection should not change the player's context — it's still their turn
        assertEquals(PlayerContext.IN_GAME, core.getPlayerContext(player1));
    }

    @Test
    void moveRejectedEvent_published_player2ContextUnchanged() {
        core.setPlayerContext(player1, PlayerContext.IN_GAME);
        core.setPlayerContext(player2, PlayerContext.IN_GAME);

        MoveRejectedEvent rejection = new MoveRejectedEvent(
                UUID.randomUUID().toString(), "session-1", player1,
                PlatformError.INVALID_MOVE, "Move is not valid.");

        bus.publish(rejection);

        // player2 is unaffected entirely
        assertEquals(PlayerContext.IN_GAME, core.getPlayerContext(player2));
    }

    // ── Unsubscribe end-to-end ───────────────────────────────────────────────

    @Test
    void eventBus_unsubscribeHandler_handlerNoLongerReceivesEvents() {
        int[] callCount = {0};
        ca.ucalgary.seng300.events.EventHandler counter = e -> callCount[0]++;

        bus.subscribe("MatchFound", counter);
        bus.unsubscribe("MatchFound", counter);

        MatchFoundEvent event = new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(player1), "tictactoe", "room-1");
        bus.publish(event);

        assertEquals(0, callCount[0]);
    }

    @Test
    void eventBus_unsubscribeOneOfTwoHandlers_otherHandlerStillReceivesEvents() {
        int[] countA = {0};
        int[] countB = {0};
        ca.ucalgary.seng300.events.EventHandler handlerA = e -> countA[0]++;
        ca.ucalgary.seng300.events.EventHandler handlerB = e -> countB[0]++;

        bus.subscribe("MatchFound", handlerA);
        bus.subscribe("MatchFound", handlerB);
        bus.unsubscribe("MatchFound", handlerA);

        MatchFoundEvent event = new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(player1), "tictactoe", "room-1");
        bus.publish(event);

        assertEquals(0, countA[0]);
        assertEquals(1, countB[0]);
    }

}