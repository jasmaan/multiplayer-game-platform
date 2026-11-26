package ca.ucalgary.seng300.notification;

import ca.ucalgary.seng300.core.PlayerContext;
import ca.ucalgary.seng300.events.ChatMessageEvent;
import ca.ucalgary.seng300.events.MatchFoundEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Routes notifications to players based on their current context.
 * Handles turn alerts, match-found alerts, and game-over alerts.
 * Consumed by PlatformCore to decide how/where to notify each player.
 */
public class NotificationService {

    /**
     * Callback interface for pushing notifications to the active GUI controller.
     * Register via setListener() when a new screen loads; clear on logout.
     */
    public interface NotificationListener {
        void onNotification(String playerId, String message);

        /** Called when a match is found. Override to show a match-found modal. */
        default void onMatchFound(MatchFoundEvent event) {}

        /** Called when a friend request is received. Override to update badge/list. */
        default void onFriendRequestReceived(String fromUsername) {}

        /** Called when a chat message arrives in a game session. */
        default void onChatMessage(ChatMessageEvent event) {}
    }

    //logs previously sent notifications
    private final List<String> notificationLog = new ArrayList<String>();

    private NotificationListener listener;

    public NotificationService() {}

    /**
     * Register a GUI listener that receives every notification in real time.
     * Pass null to detach (e.g. on logout or screen transition).
     */
    public void setListener(NotificationListener listener) {
        this.listener = listener;
    }

    public NotificationListener getListener() {
        return listener;
    }

    /**
     * Deliver a notification to a player.
     * Routes to the player's active GUI view when one is attached;
     * falls back to console output until a GUI layer is wired in.
     * @param playerId the ID of the player to notify
     * @param message the message to deliver
     */
    public void notifyPlayer(String playerId, String message) {
        //checks if a player id was inputted
        if(playerId == null || playerId.isBlank()) {
            //print error if player id is left blank
            System.err.println("[NOTIFY] Warning: null or empty playerId, message dropped: " + message);
            return;
        }
        //checks if message was null
        if (message == null || message.isBlank()) {
            //print error if message is empty
            System.err.println("[NOTIFY] Warning: null or empty message for player: " + playerId);
            return;
        }

        //create a new entry to log in the notification log
        String entry = "[" + Instant.now() + "] [NOTIFY] " + playerId + ": " + message;
        notificationLog.add(entry);

        //print the notification to terminal
        System.out.println(entry);

        // Push to GUI if a listener is registered
        if (listener != null) {
            listener.onNotification(playerId, message);
        }
    }

    /**
     * Log a notification to the console and notification log without
     * triggering the generic onNotification listener callback.
     * Used by specialized notification methods that have their own
     * dedicated listener callbacks (e.g. onMatchFound, onFriendRequestReceived).
     */
    private void logNotification(String playerId, String message) {
        if (playerId == null || playerId.isBlank() || message == null || message.isBlank()) return;
        String entry = "[" + Instant.now() + "] [NOTIFY] " + playerId + ": " + message;
        notificationLog.add(entry);
        System.out.println(entry);
    }

    /**
     * Create a turn alert for a player whose turn has arrived.
     * @param playerId the player whose turn it is
     * @param sessionId the session which the turn belongs to
     * @param context the player's current location in the app
     */
    public void createTurnAlert(String playerId, String sessionId, PlayerContext context) {
        //delivers corresponding notification format based on where the player is currently tabbed in on the platform
        if(context == PlayerContext.IN_GAME){
            notifyPlayer(playerId, "Your turn — make your move in session " + sessionId);
        }else if(context == PlayerContext.VIEWING_RESULTS){
            notifyPlayer(playerId, "[ALERT] You have another turn waiting in session " + sessionId);
        }else{
            notifyPlayer(playerId, "[ALERT] Your turn is waiting in session " + sessionId);
        }
    }

    /**
     * Create a game-over alert with the result.
     * @param playerId the player to notify
     * @param sessionId the session that just ended
     * @param result the outcome of the game for the player notified
     * @param context the player's current location on the platform
     */
    public void createGameOverAlert(String playerId, String sessionId, String result, PlayerContext context) {
        //based on player context, deliver notification directly in the window or as an alert
        if(context == PlayerContext.IN_GAME){
            notifyPlayer(playerId, "Game over in session — " + result);
        }else{
            notifyPlayer(playerId, "[ALERT] Game over in session " + sessionId + " — " + result);
        }
    }

    /**
     * Create a match-found alert prompting the player to join.
     * @param playerId the player to notify
     * @param sessionId the session they have been matched into
     */
    public void createMatchFoundAlert(String playerId, String sessionId) {
        notifyPlayer(playerId, "Match found! Join session " + sessionId);
    }

    /**
     * Deliver a MatchFoundEvent to the registered GUI listener.
     * Also logs a terminal entry for each matched player.
     * @param event the match-found event from the EventBus
     */
    public void notifyMatchFound(MatchFoundEvent event) {
        for (String playerId : event.getPlayers()) {
            logNotification(playerId, "Match found! Join session " + event.getRoomId());
        }
        if (listener != null) {
            listener.onMatchFound(event);
        }
    }

    /**
     * Deliver a friend-request notification.
     * Logs the event and routes it to the registered listener.
     * @param toPlayerId   the player who received the request
     * @param fromPlayerId the player who sent the request
     */
    public void notifyFriendRequest(String toPlayerId, String fromPlayerId) {
        logNotification(toPlayerId, "Friend request from " + fromPlayerId);
        if (listener != null) {
            listener.onFriendRequestReceived(fromPlayerId);
        }
    }

    /**
     * Deliver a chat message notification.
     * Logs the event and routes it to the registered listener.
     * @param event the chat message event
     */
    public void notifyChatMessage(ChatMessageEvent event) {
        logNotification(event.getSenderId(),
                "Chat in session " + event.getSessionId() + ": " + event.getText());
        if (listener != null) {
            listener.onChatMessage(event);
        }
    }

    /**
     * Returns an unmodifiable view of the notification log
     * @return list of all notification entries with timestamps
     */
    public List<String> getNotificationLog() {
        return Collections.unmodifiableList(notificationLog);
    }

    /**
     * Clears the notification log
     */
    public void clearNotificationLog() {
        notificationLog.clear();
    }
}
