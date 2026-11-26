package ca.ucalgary.seng300.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.ucalgary.seng300.events.ChatMessageEvent;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.notification.NotificationService;
import ca.ucalgary.seng300.session.GameEngine;
import ca.ucalgary.seng300.session.MoveSubmissionResult;
import ca.ucalgary.seng300.validation.Validation;
import ca.ucalgary.seng300.validation.ValidationError;

/**
 * Verifies the full event pipeline ends at the GUI:
 * GameSessionManager → EventBus → PlatformCore → NotificationService → NotificationListener (GUI)
 *
 * The NotificationListener has four optional callbacks:
 *   - onNotification           (generic; fired by notifyPlayer)
 *   - onMatchFound             (fired by notifyMatchFound)
 *   - onChatMessage            (fired by notifyChatMessage)
 *   - onFriendRequestReceived  (fired by notifyFriendRequest)
 *
 * The fakeGui below captures all four so each test can assert on the right channel.
 */
class NotificationListenerTest {

    private PlatformCore platform;
    private String alice;
    private String bob;

    /** Captures everything delivered to the listener (simulates a GUI controller). */
    private final List<String> guiReceived = new ArrayList<>();
    private final List<MatchFoundEvent> matchFoundReceived = new ArrayList<>();
    private final List<ChatMessageEvent> chatReceived = new ArrayList<>();
    private final List<String> friendRequestReceived = new ArrayList<>();

    private final NotificationService.NotificationListener fakeGui =
            new NotificationService.NotificationListener() {
                @Override
                public void onNotification(String playerId, String message) {
                    guiReceived.add(playerId + ": " + message);
                }
                @Override
                public void onMatchFound(MatchFoundEvent event) {
                    matchFoundReceived.add(event);
                }
                @Override
                public void onChatMessage(ChatMessageEvent event) {
                    chatReceived.add(event);
                }
                @Override
                public void onFriendRequestReceived(String fromUsername) {
                    friendRequestReceived.add(fromUsername);
                }
            };

    @BeforeEach
    void setUp() {
        platform = new PlatformCore();
        platform.getSessionManager().registerEngine("TestGame", new FakeGameEngine());

        alice = platform.registerPlayer("alice", "smith", "Password1!");
        bob   = platform.registerPlayer("bob",   "jones", "Password1!");

        platform.login(alice, "Password1!");
        platform.login(bob,   "Password1!");

        platform.getNotificationService().clearNotificationLog();
        guiReceived.clear();
        matchFoundReceived.clear();
        chatReceived.clear();
        friendRequestReceived.clear();
    }

    @Test
    void listener_receivesMatchFoundNotification() {
        platform.getNotificationService().setListener(fakeGui);

        MatchFoundEvent event = new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", "room-1");
        platform.getEventBus().publish(event);

        // notifyMatchFound routes to the dedicated onMatchFound callback,
        // not the generic onNotification channel.
        assertEquals(1, matchFoundReceived.size());
        assertTrue(matchFoundReceived.get(0).getPlayers().contains(alice));
        assertTrue(matchFoundReceived.get(0).getPlayers().contains(bob));
    }

    @Test
    void listener_receivesGameOverNotification() {
        platform.getNotificationService().setListener(fakeGui);

        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));
        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));

        platform.submitMove(sessionId, alice, new TestMove(alice, true, true));

        assertTrue(guiReceived.stream().anyMatch(e -> e.contains(alice) && e.contains("You won")));
        assertTrue(guiReceived.stream().anyMatch(e -> e.contains(bob)   && e.contains("You lost")));
    }

    @Test
    void listener_receivesMoveRejectedNotification() {
        platform.getNotificationService().setListener(fakeGui);

        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));
        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));

        // Alice submits an invalid move
        platform.submitMove(sessionId, alice, new TestMove(alice, false, false));

        assertTrue(guiReceived.stream().anyMatch(e -> e.contains(alice) && e.contains("Move rejected")));
    }

    @Test
    void listener_notCalledAfterDetach() {
        platform.getNotificationService().setListener(fakeGui);
        platform.getNotificationService().setListener(null); // detach (e.g. logout)

        // Trigger an event that *would* have populated guiReceived if the listener
        // were still attached: an invalid move produces a MoveRejected → notifyPlayer
        // → onNotification call.
        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));
        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));
        platform.submitMove(sessionId, alice, new TestMove(alice, false, false));

        // Also trigger a match-found event; the dedicated callback should also stay silent.
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", "room-2"));

        assertTrue(guiReceived.isEmpty(),       "onNotification should not fire after detach");
        assertTrue(matchFoundReceived.isEmpty(), "onMatchFound should not fire after detach");
    }

    @Test
    void listener_replacedByNewController_onlyNewOneReceives() {
        List<MatchFoundEvent> firstGui  = new ArrayList<>();
        List<MatchFoundEvent> secondGui = new ArrayList<>();

        platform.getNotificationService().setListener(new NotificationService.NotificationListener() {
            @Override public void onNotification(String p, String m) {}
            @Override public void onMatchFound(MatchFoundEvent e) { firstGui.add(e); }
        });
        platform.getNotificationService().setListener(new NotificationService.NotificationListener() {
            @Override public void onNotification(String p, String m) {}
            @Override public void onMatchFound(MatchFoundEvent e) { secondGui.add(e); }
        });

        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", "room-1"));

        assertTrue(firstGui.isEmpty(),  "old listener should not receive after replacement");
        assertFalse(secondGui.isEmpty(), "new listener should receive notifications");
    }

    @Test
    void fullPipeline_submitMoveToGuiNotification() {
        platform.getNotificationService().setListener(fakeGui);

        String roomId = UUID.randomUUID().toString();
        platform.getEventBus().publish(new MatchFoundEvent(
                UUID.randomUUID().toString(), List.of(alice, bob), "TestGame", roomId));
        String sessionId = platform.getSessionManager()
                .startSession(roomId, "TestGame", List.of(alice, bob));

        // Mid-game move — both IN_GAME so they get "Game updated" notifications
        MoveSubmissionResult result = platform.submitMove(sessionId, alice,
                new TestMove(alice, true, false));

        assertTrue(result.isAccepted());
        assertFalse(result.getUpdate().isGameOver());
        // alice and bob are IN_GAME, so they each get a "Game updated" notification via listener
        assertTrue(guiReceived.stream().anyMatch(e -> e.contains("Game updated") || e.contains("turn")));
    }

    @Test
    void listener_receivesChatMessageNotification() {
        platform.getNotificationService().setListener(fakeGui);

        String sessionId = UUID.randomUUID().toString();
        platform.sendChatMessage(sessionId, alice, "hello bob");

        assertEquals(1, chatReceived.size());
        ChatMessageEvent received = chatReceived.get(0);
        assertEquals(alice, received.getSenderId());
        assertEquals(sessionId, received.getSessionId());
        assertEquals("hello bob", received.getText());
    }

    @Test
    void listener_doesNotReceiveBlankChatMessage() {
        platform.getNotificationService().setListener(fakeGui);

        // ChatValidator rejects empty messages, so no event should ever fire.
        platform.sendChatMessage(UUID.randomUUID().toString(), alice, "   ");

        assertTrue(chatReceived.isEmpty());
    }

    @Test
    void listener_receivesFriendRequestNotification() {
        platform.getNotificationService().setListener(fakeGui);

        // bob was the most recent login in setUp, so currentUser == bob.
        // Sending a request from bob to alice should trigger onFriendRequestReceived(bob).
        String error = platform.sendFriendRequest(alice);

        assertEquals(null, error, "friend request should succeed");
        assertEquals(1, friendRequestReceived.size());
        assertEquals(bob, friendRequestReceived.get(0));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static class FakeGameEngine implements GameEngine {
        @Override public Object createInitialState(List<String> playerIds) { return new TestBoard(); }
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
        @Override public Object applyMove(Object board, Object move) {
            TestBoard next = new TestBoard();
            next.moveCount = ((TestBoard) board).moveCount + 1;
            if (((TestMove) move).winningMove) next.winnerId = ((TestMove) move).playerId;
            return next;
        }
        @Override public boolean evaluateEnd(Object board) { return ((TestBoard) board).winnerId != null; }
        @Override public String getWinner(Object board)    { return ((TestBoard) board).winnerId; }
    }

    private static class TestBoard {
        int moveCount = 0; String winnerId = null;
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
