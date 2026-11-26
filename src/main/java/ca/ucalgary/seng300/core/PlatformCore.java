package ca.ucalgary.seng300.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import ca.ucalgary.seng300.events.ChatMessageEvent;
import ca.ucalgary.seng300.events.EventBus;
import ca.ucalgary.seng300.events.EventHandler;
import ca.ucalgary.seng300.events.GameEvent;
import ca.ucalgary.seng300.events.GameStateUpdate;
import ca.ucalgary.seng300.events.MatchFoundEvent;
import ca.ucalgary.seng300.events.MoveRejectedEvent;
import ca.ucalgary.seng300.events.RoomJoinedEvent;
import ca.ucalgary.seng300.games.Checkers;
import ca.ucalgary.seng300.games.ConnectFour;
import ca.ucalgary.seng300.games.TicTacToe;
import ca.ucalgary.seng300.leaderboard.Leaderboard;
import ca.ucalgary.seng300.matchmaking.MatchMaking;
import ca.ucalgary.seng300.notification.NotificationService;
import ca.ucalgary.seng300.persistence.CredentialsRepository;
import ca.ucalgary.seng300.persistence.Database;
import ca.ucalgary.seng300.persistence.PresenceService;
import ca.ucalgary.seng300.persistence.UserRepository;
import ca.ucalgary.seng300.room.Room;
import ca.ucalgary.seng300.room.RoomManager;
import ca.ucalgary.seng300.session.GameSession;
import ca.ucalgary.seng300.session.GameSessionManager;
import ca.ucalgary.seng300.session.MoveSubmissionResult;
import ca.ucalgary.seng300.validation.ChatValidator;
import ca.ucalgary.seng300.validation.Validation;
import ca.ucalgary.seng300.validation.ValidationError;

/**
 * Central orchestrator for the platform.
 * All UI and game interactions flow through PlatformCore.
 * Routes events, manages notifications, and coordinates sub-services.
 */
public class PlatformCore implements EventHandler {

    private final EventBus eventBus;
    private final RoomManager roomManager;
    private final GameSessionManager sessionManager;
    private final MatchMaking matchMaking;
    private final Leaderboard leaderboard;
    private final NotificationService notificationService;
    private final Database database;
    private final CredentialsRepository credentialsRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final Map<String, PlayerContext> playerContexts;
    private String currentUser;
    private PlayerProfile currentProfile;
    private final Map<String, Consumer<ChatMessageEvent>> chatCallbacks = new HashMap<>();
    private final ScheduledExecutorService matchmakingScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Set<String> QUEUE_GAME_TYPES = Set.of("Checkers", "TicTacToe", "ConnectFour");

    // ── Play-again coordination ──────────────────────────────────────────────
    // sessionId → playerId who already clicked "Play Again"
    private final Map<String, String> playAgainRequests = new HashMap<>();
    // sessionId:playerId → callback fired when the opponent clicks "End Match" while waiting
    private final Map<String, Runnable> playAgainEndedCallbacks = new HashMap<>();
    // Callback fired on the game screen when the opponent forfeits mid-game (receives winner points)
    private Consumer<String> forfeitWinCallback;

    public PlatformCore() {
        this.eventBus = new EventBus();
        this.sessionManager = new GameSessionManager(this.eventBus);
        this.roomManager = new RoomManager(this.sessionManager);
        this.database = new Database();
        this.leaderboard = new Leaderboard(database);
        this.matchMaking = new MatchMaking(this.roomManager, this.eventBus, this.leaderboard);
        this.notificationService = new NotificationService();
        this.presenceService = new PresenceService();
        this.credentialsRepository = new CredentialsRepository();
        this.userRepository = new UserRepository();
        this.playerContexts = new HashMap<>();
        this.currentUser = null;

        // Seed in-memory store from persisted credentials and profiles
        credentialsRepository.loadAll().forEach(
                (username, hash) -> database.storeData("credentials:" + username, hash));
        userRepository.loadAll().forEach((username, profile) -> {
            database.storeData("profile:" + username, profile);
            userRepository.save(profile); // flush normalized rank back to disk
            // Re-seed in-memory leaderboard rankings from persisted MMR
            profile.getMmrByGame().forEach((gameType, mmr) ->
                    leaderboard.updatePlayerRankingToMmr(username, gameType, mmr));
        });

        // Seed DinoBot computer player (created once; never written to disk)
        if (!database.exists("profile:DinoBot")) {
            PlayerProfile botProfile = new PlayerProfile("DinoBot", "Dino", "Bot", "");
            botProfile.setCurrentRank("Gold");
            botProfile.setAvatarPath("/images/DinoBot_Avatar.png");
            database.storeData("profile:DinoBot", botProfile);
            database.storeData("credentials:DinoBot", "");
        }

        registerDefaultEngines(); // Register default game engines at startup

        // Subscribe to all event types
        this.eventBus.subscribe("GameStateUpdate", this);
        this.eventBus.subscribe("MoveRejected", this);
        this.eventBus.subscribe("MatchFound", this);
        this.eventBus.subscribe("RoomJoined", this);
        this.eventBus.subscribe("ChatMessage", this);

        // Periodically attempt matching so MMR range expansion takes effect
        // Found out about how to use from
        //https://stackoverflow.com/questions/24649842/scheduleatfixedrate-vs-schedulewithfixeddelay
        matchmakingScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                for (String gameType : QUEUE_GAME_TYPES) {
                    tryMatch(gameType);
                }
            }
        }, 5, 5, TimeUnit.SECONDS);

    }

    @Override
    public void handle(GameEvent event) {
        if (event instanceof GameStateUpdate update) {
            handleGameStateUpdate(update);
        } else if (event instanceof MoveRejectedEvent rejected) {
            handleMoveRejected(rejected);
        } else if (event instanceof MatchFoundEvent matchFound) {
            handleMatchFound(matchFound);
        } else if (event instanceof RoomJoinedEvent roomJoined) {
            handleRoomJoined(roomJoined);
        } else if (event instanceof ChatMessageEvent chatMessage) {
            handleChatMessage(chatMessage);
        }
    }

    /**
     * Authenticate a player using the Database credential stub.
     * On success, sets the player's context to DASHBOARD and tracks them as the current user.
     *
     * @param username the player's username
     * @param password the player's password
     * @return true if authentication succeeded, false otherwise
     */
    public boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        // Check credentials against the Database stub
        // Credentials are stored as "credentials:<username>" -> hashed password
        Object stored = database.retrieveData("credentials:" + username);
        if (!hashPassword(password).equals(stored)) {
            return false;
        }

        // Prevent duplicate login
        if (isPlayerLoggedIn(username)) {
            return false;
        }

        currentUser = username;
        currentProfile = getPlayerProfile(username);
        setPlayerContext(username, PlayerContext.DASHBOARD);
        presenceService.goOnline(username);
        return true;
    }

    /**
     * Log out a player: clear their context, remove from any active queues,
     * and reset current user tracking.
     *
     * @param playerId the player to log out
     */
    public void logout(String playerId) {
        if (!isPlayerLoggedIn(playerId)) {
            return;
        }

        // Clean up: remove from queue if they were queued
        PlayerContext ctx = getPlayerContext(playerId);
        if (ctx == PlayerContext.IN_QUEUE) {
            matchMaking.removePlayer(playerId);
        }

        removePlayerContext(playerId);
        presenceService.goOffline(playerId);
        if (playerId.equals(currentUser)) {
            currentUser = null;
            currentProfile = null;
        }
    }

    /**
     * Builds the base username from first initial + last name, lowercased.
     * e.g. ("John", "Doe") -> "jdoe"
     * The numeric suffix is assigned by registerPlayer to guarantee uniqueness.
     */
    public String generateUsername(String firstName, String lastName) {
        if (firstName == null || lastName == null) {
            return null;
        }
        return (firstName.charAt(0) + lastName).toLowerCase();
    }

    public Validation validateCredentials(String firstname, String lastname, String password) {

        String nameRegex = "^[a-zA-Z][a-zA-Z '-]{1,49}$";
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).{8,64}$";

        if (firstname == null || !firstname.matches(nameRegex)) {
            return Validation.failure(ValidationError.INVALID_FORMAT, "Invalid first name");
        }

        if (lastname == null || !lastname.matches(nameRegex)) {
            return Validation.failure(ValidationError.INVALID_FORMAT, "Invalid last name");
        }

        if (password == null || password.length() < 8) {
            return Validation.failure(ValidationError.TOO_SHORT, "Password is too short");
        }

        if (!password.matches(passwordRegex)) {
            return Validation.failure(ValidationError.INVALID_FORMAT, "Password does not meet requirements");
        }

        return Validation.success();
    }

    /**
     * Register a new player's credentials in the Database stub.
     * This is a convenience method for setting up test/demo data.
     */
    public String registerPlayer(String firstname, String lastname, String password) {
        Validation result = validateCredentials(firstname, lastname, password);
        if (!result.isValid()) {
            return null;
        }
        String base = generateUsername(firstname, lastname);
        String countKey = "usernameCount:" + base;

        Object stored = database.retrieveData(countKey);
        int count = (stored == null) ? 1 : (int) stored + 1;
        database.storeData(countKey, count);

        String username = base + count;
        String passwordHash = hashPassword(password);

        // Store credentials in-memory and persist to disk
        database.storeData("credentials:" + username, passwordHash);
        credentialsRepository.save(username, passwordHash);

        // Store full player profile in-memory and persist to disk
        PlayerProfile profile = new PlayerProfile(username, firstname, lastname, passwordHash);
        database.storeData("profile:" + username, profile);
        userRepository.save(profile);

        return username;
    }

    /**
     * Retrieve a player's full profile from the database.
     * @return the PlayerProfile, or null if not found
     */
    public PlayerProfile getPlayerProfile(String username) {
        Object profile = database.retrieveData("profile:" + username);
        return (profile instanceof PlayerProfile) ? (PlayerProfile) profile : null;
    }

    /**
     * Hash a password using SHA-256. The stored hash is compared on login
     * so the plain-text password is never persisted.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Get the currently logged-in user on this client.
     * @return the current user's ID, or null if no one is logged in
     */
    public String getCurrentUser() {
        return currentUser;
    }

    /**
     * Returns the profile loaded at login for the currently active player.
     * @return the current PlayerProfile, or null if no one is logged in
     */
    public PlayerProfile getCurrentProfile() {
        return currentProfile;
    }

    /**
     * Add a player to the matchmaking queue and update their context to IN_QUEUE.
     */
    public void joinQueue(String playerId, String gameType) {
        matchMaking.addPlayerToQueue(playerId, gameType);
        setPlayerContext(playerId, PlayerContext.IN_QUEUE);
        tryMatch(gameType);
    }

    private void tryMatch(String gameType) {
        // Only auto-match when a UI listener is registered; avoids interfering with
        // tests that control MatchFoundEvent publication themselves.
        if (notificationService.getListener() == null) return;

        List<String> matched = matchMaking.matchPlayers(gameType);
        if (matched.size() < 2) return;

        String roomId = roomManager.createRoom(matched.get(0), gameType, 2).getRoomId();
        for (String pid : matched) {
            roomManager.joinRoom(roomId, pid);
        }
        eventBus.publish(new MatchFoundEvent(
                java.util.UUID.randomUUID().toString(), matched, gameType, roomId));
    }

    /**
     * Remove a player from the matchmaking queue and return them to DASHBOARD.
     */
    public void leaveQueue(String playerId) {
        matchMaking.removePlayer(playerId);
        setPlayerContext(playerId, PlayerContext.DASHBOARD);
    }

    /**
     * Submit a move for a player in an active session.
     * Delegates to GameSessionManager, then publishes either
     * a GameStateUpdate or a MoveRejectedEvent to the EventBus so all subscribers are notified.
     *
     * @return the GameStateUpdate produced, or null if the move was invalid
     */
    public MoveSubmissionResult submitMove(String sessionId, String playerId, Object move) {
        MoveSubmissionResult result = sessionManager.submitMove(sessionId, playerId, move);
        if (result == null) {
            return null;
        }

        if (result.isAccepted()) {
            eventBus.publish(result.getUpdate());
        } else {
            eventBus.publish(result.getRejection());
        }

        return result;
    }

    /**
     * Register the default game engines.
     */
    private void registerDefaultEngines() {
        sessionManager.registerEngine("TicTacToe", new TicTacToe());
        sessionManager.registerEngine("ConnectFour", new ConnectFour());
        sessionManager.registerEngine("Checkers", new Checkers());
    }

    public void returnToDashboard(String playerId) {
        setPlayerContext(playerId, PlayerContext.DASHBOARD);
    }

    /**
     * Route a game state update to each recipient based on their current context.
     * - IN_GAME players get direct board/view updates
     * - DASHBOARD/IN_LOBBY players get turn alerts or game-over banners
     * - If the game is over, transition all recipients to VIEWING_RESULTS
     */
    public void handleGameStateUpdate(GameStateUpdate update) {
        if (update.isGameOver()) {
            GameSession session = sessionManager.getSession(update.getSessionId());
            if (session != null) {
                String gameType = session.getGameType().toLowerCase();
                String winnerId = update.getWinnerId();
                List<String> sessionPlayers = session.getPlayers();
                String loserId = sessionPlayers.stream()
                        .filter(p -> !p.equals(winnerId)).findFirst().orElse(null);
                leaderboard.logGameResult(update.getSessionId(), winnerId, loserId, winnerId == null);
                for (String pid : sessionPlayers) {
                    PlayerProfile profile = getPlayerProfile(pid);
                    if (profile == null) continue;
                    String opponent = sessionPlayers.stream()
                            .filter(p -> !p.equals(pid)).findFirst().orElse(null);
                    if (winnerId != null && winnerId.equals(pid)) profile.recordWin(gameType);
                    if (opponent != null) profile.recordMatch(gameType, opponent);
                    profile.addRecentGame(gameType);
                    String outcome = (winnerId == null) ? "draw" : (pid.equals(winnerId) ? "win" : "loss");
                    leaderboard.updatePlayerRanking(pid, gameType, outcome);
                    profile.setMmr(gameType, leaderboard.getPlayerMmr(pid, gameType));
                    profile.setCurrentRank(leaderboard.getOverallTier(pid));
                    userRepository.save(profile);
                    if (pid.equals(currentUser)) currentProfile = profile;
                }
            }
        }

        for (String playerId : update.getRecipients()) {
            PlayerContext ctx = playerContexts.getOrDefault(playerId, PlayerContext.DASHBOARD);

            if (update.isGameOver()) {
                // Game ended — notify and transition to results screen
                String result;
                if (update.getWinnerId() == null) {
                    result = "It's a draw!";
                } else if (playerId.equals(update.getWinnerId())) {
                    result = "You won!";
                } else {
                    result = "You lost.";
                }
                notificationService.createGameOverAlert(playerId, update.getSessionId(), result, ctx);
                setPlayerContext(playerId, PlayerContext.VIEWING_RESULTS);
            } else if (ctx == PlayerContext.IN_GAME) {
                // Player is actively in the game — push board update directly
                notificationService.notifyPlayer(playerId,
                        "Game updated in session " + update.getSessionId());
            } else {
                // Player is on dashboard/lobby/queue — send a turn alert if it's their turn
                if (playerId.equals(update.getActivePlayerId())) {
                    notificationService.createTurnAlert(playerId, update.getSessionId(), ctx);
                }
            }
        }
    }

    /**
     * Route a move rejection back to the player who submitted the invalid move.
     */
    private void handleMoveRejected(MoveRejectedEvent rejected) {
        notificationService.notifyPlayer(rejected.getPlayerId(),
                "Move rejected: " + rejected.getMessage());
    }

    /**
     * Handle a match-found event: notify all matched players and transition
     * them from IN_QUEUE to IN_GAME context.
     */
    private void handleMatchFound(MatchFoundEvent matchFound) {
        for (String playerId : matchFound.getPlayers()) {
            setPlayerContext(playerId, PlayerContext.IN_GAME);
        }
        notificationService.notifyMatchFound(matchFound);
    }

    // ── Play-again coordination ──────────────────────────────────────────────

    /**
     * Register the current player's intent to play again.
     * Returns true if BOTH players have now requested (caller should start the new game immediately).
     * Returns false if the caller must wait; onOpponentEndedMatch fires if the opponent
     * clicks "End Match" instead of "Play Again".
     */
    public boolean requestPlayAgain(String sessionId, String playerId, Runnable onOpponentEndedMatch) {
        String waiting = playAgainRequests.get(sessionId);
        if (waiting != null && !waiting.equals(playerId)) {
            // Other player is already waiting — both ready
            playAgainRequests.remove(sessionId);
            playAgainEndedCallbacks.remove(sessionId + ":" + waiting);
            return true;
        }
        playAgainRequests.put(sessionId, playerId);
        playAgainEndedCallbacks.put(sessionId + ":" + playerId, onOpponentEndedMatch);
        return false;
    }

    /** Cancel a previously registered play-again request for this player. */
    public void cancelPlayAgain(String sessionId, String playerId) {
        playAgainRequests.remove(sessionId);
        playAgainEndedCallbacks.remove(sessionId + ":" + playerId);
    }

    /**
     * Called when a player clicks "End Match" on the result screen.
     * If the opponent was waiting for play-again, fire their callback so they can navigate away.
     */
    public void notifyOpponentEndedMatch(String sessionId, String endingPlayerId) {
        playAgainRequests.remove(sessionId);
        // Find the OTHER player's callback
        String keyPrefix = sessionId + ":";
        playAgainEndedCallbacks.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(keyPrefix) && !e.getKey().equals(keyPrefix + endingPlayerId)) {
                javafx.application.Platform.runLater(e.getValue());
                return true;
            }
            return false;
        });
        // Also remove the ending player's own pending entry
        playAgainEndedCallbacks.remove(keyPrefix + endingPlayerId);
    }

    /** Set a callback to receive a forfeit-win message (opponent forfeited mid-game). */
    public void setForfeitWinCallback(Consumer<String> callback) {
        this.forfeitWinCallback = callback;
    }

    // ---- Player Context Tracking ----

    /**
     * Get the current navigation context for a player.
     * @return the player's context, or null if the player is not logged in
     */
    public PlayerContext getPlayerContext(String playerId) {
        return playerContexts.get(playerId);
    }

    /**
     * Update a player's navigation context.
     * Called internally when the player transitions between screens.
     */
    void setPlayerContext(String playerId, PlayerContext context) {
        playerContexts.put(playerId, context);
    }

    /**
     * Remove a player from context tracking (on logout).
     */
    void removePlayerContext(String playerId) {
        playerContexts.remove(playerId);
    }

    /**
     * Check if a player is currently logged in on this instance (has an active context).
     */
    public boolean isPlayerLoggedIn(String playerId) {
        return playerContexts.containsKey(playerId);
    }

    /**
     * Check if a player is online on any instance (local context OR cross-instance heartbeat).
     */
    public boolean isPlayerOnline(String playerId) {
        return playerContexts.containsKey(playerId) || presenceService.isOnline(playerId);
    }

    /**
     * Check whether a plain-text password matches the stored hash for a given user.
     * Used by the settings panel to verify the old password before allowing a change.
     *
     * @param username      the player's username
     * @param plainPassword the plain-text password to verify
     * @return true if the password matches, false otherwise
     */
    public boolean verifyPassword(String username, String plainPassword) {
        Object stored = database.retrieveData("credentials:" + username);
        return stored != null && hashPassword(plainPassword).equals(stored);
    }

    /**
     * Update a player's password. Validates strength then persists the new hash.
     * PlayerProfile.passwordHash is final so only the credentials store is updated.
     *
     * @param username    the player's username
     * @param newPassword the new plain-text password (must pass strength validation)
     * @return true on success, false if newPassword fails validation
     */
    public boolean updatePassword(String username, String newPassword) {
        String regex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).{8,64}$";
        if (newPassword == null || !newPassword.matches(regex)) return false;
        String hash = hashPassword(newPassword);
        database.storeData("credentials:" + username, hash);
        credentialsRepository.save(username, hash);
        return true;
    }

    /**
     * Rename a player's account. Migrates their profile, credentials, and active
     * context to the new username and persists both repositories.
     *
     * @param oldUsername the player's current username
     * @param newUsername the desired new username
     * @return true on success; false if newUsername is invalid, already taken, or the player is not found
     */
    public boolean updateUsername(String oldUsername, String newUsername) {
        if (newUsername == null || newUsername.isBlank()
                || !newUsername.matches("^[a-zA-Z0-9_]{3,30}$")) return false;
        if (newUsername.equals(oldUsername)) return true;
        if (database.exists("profile:" + newUsername)) return false;

        PlayerProfile old = getPlayerProfile(oldUsername);
        if (old == null) return false;

        String hash = (String) database.retrieveData("credentials:" + oldUsername);

        PlayerProfile updated = new PlayerProfile(newUsername, old.getFirstName(),
                old.getLastName(), hash != null ? hash : old.getPasswordHash());
        updated.setCurrentRank(old.getCurrentRank());
        updated.setAvatarPath(old.getAvatarPath());
        old.getMatchesPlayed().forEach((game, opponents) ->
                opponents.forEach(opp -> updated.recordMatch(game, opp)));
        old.getWins().forEach((game, count) -> {
            for (int i = 0; i < count; i++) updated.recordWin(game);
        });
        List<String> history = old.getRecentGameHistory();
        for (int i = history.size() - 1; i >= 0; i--) updated.addRecentGame(history.get(i));

        database.storeData("profile:" + newUsername, updated);
        if (hash != null) database.storeData("credentials:" + newUsername, hash);
        database.deleteData("profile:" + oldUsername);
        database.deleteData("credentials:" + oldUsername);

        userRepository.save(updated);
        if (hash != null) credentialsRepository.save(newUsername, hash);
        userRepository.delete(oldUsername);
        credentialsRepository.delete(oldUsername);

        if (oldUsername.equals(currentUser)) {
            PlayerContext ctx = playerContexts.remove(oldUsername);
            if (ctx != null) playerContexts.put(newUsername, ctx);
            currentUser = newUsername;
            currentProfile = updated;
        }
        return true;
    }

    /**
     * Join a room on behalf of a player. Wraps RoomManager and fires a RoomJoinedEvent
     * so the host is notified via the registered callback.
     *
     * @return null on success, or a PlatformError describing why the join was rejected
     */
    public PlatformError joinRoom(String roomId, String playerId) {
        PlatformError error = roomManager.joinRoom(roomId, playerId);
        if (error == null) {
            Room room = roomManager.getRoom(roomId);
            if (room != null && !playerId.equals(room.getHostId())) {
                eventBus.publish(new RoomJoinedEvent(roomId, room.getHostId(), playerId));
            }
        }
        return error;
    }

    /**
     * Record a forfeit: the leaving player takes a loss and the remaining opponent
     * takes a win. Both match records and recent history are updated and persisted.
     * MMR and rank sync will be completed once Issue #3 (Leaderboard port) lands.
     */
    public void forfeitGame(String sessionId, String leavingPlayerId) {
        GameSession session = sessionManager.getSession(sessionId);
        if (session == null) return;

        String gameType = session.getGameType().toLowerCase();
        String winnerId = session.getPlayers().stream()
                .filter(p -> !p.equals(leavingPlayerId))
                .findFirst().orElse(null);

        leaderboard.logGameResult(sessionId, winnerId, leavingPlayerId, false);

        if (winnerId != null) {
            PlayerProfile winnerProfile = getPlayerProfile(winnerId);
            if (winnerProfile != null) {
                winnerProfile.recordWin(gameType);
                winnerProfile.recordMatch(gameType, leavingPlayerId);
                winnerProfile.addRecentGame(gameType);
                leaderboard.updatePlayerRanking(winnerId, gameType, "win");
                winnerProfile.setMmr(gameType, leaderboard.getPlayerMmr(winnerId, gameType));
                winnerProfile.setCurrentRank(leaderboard.getOverallTier(winnerId));
                userRepository.save(winnerProfile);
                if (winnerId.equals(currentUser) && forfeitWinCallback != null) {
                    String msg = leavingPlayerId + " forfeited — you won! +25 pts awarded.";
                    javafx.application.Platform.runLater(() -> forfeitWinCallback.accept(msg));
                }
            }
        }

        PlayerProfile leaverProfile = getPlayerProfile(leavingPlayerId);
        if (leaverProfile != null) {
            if (winnerId != null) leaverProfile.recordMatch(gameType, winnerId);
            leaverProfile.addRecentGame(gameType);
            leaderboard.updatePlayerRanking(leavingPlayerId, gameType, "loss");
            leaverProfile.setMmr(gameType, leaderboard.getPlayerMmr(leavingPlayerId, gameType));
            leaverProfile.setCurrentRank(leaderboard.getOverallTier(leavingPlayerId));
            userRepository.save(leaverProfile);
            if (leavingPlayerId.equals(currentUser)) {
                currentProfile = leaverProfile;
            }
        }
    }

    /**
     * Publish a chat message for a session. Both players' registered callbacks
     * will receive the event via handleChatMessage().
     */
    public void sendChatMessage(String sessionId, String senderId, String text) {
        if (!new ChatValidator().validate(text).isValid()) return;
        eventBus.publish(new ChatMessageEvent(sessionId, senderId, text));
    }

    /**
     * Register a callback that fires whenever a chat message arrives for the given session.
     * Pass null to unregister (call when navigating away from the game screen).
     */
    public void setChatMessageCallback(String sessionId, Consumer<ChatMessageEvent> callback) {
        if (callback == null) {
            chatCallbacks.remove(sessionId);
        } else {
            chatCallbacks.put(sessionId, callback);
        }
    }

    private void handleRoomJoined(RoomJoinedEvent event) {
        if (event.getHostId().equals(currentUser)) {
            notificationService.notifyPlayer(event.getHostId(),
                    event.getJoiningPlayerId() + " joined your room! Head to Game Room to start.");
        }
    }

    private void handleChatMessage(ChatMessageEvent event) {
        notificationService.notifyChatMessage(event);
        Consumer<ChatMessageEvent> callback = chatCallbacks.get(event.getSessionId());
        if (callback != null) {
            javafx.application.Platform.runLater(() -> callback.accept(event));
        }
    }

    /**
     * Returns all persisted player profiles sorted by tier (Gold > Silver > Bronze/other),
     * then by total MMR descending, then by total wins descending.
     * Used by the dashboard leaderboard.
     */
    public List<PlayerProfile> getAllProfiles() {
        return userRepository.loadAll().values().stream()
                .sorted((a, b) -> {
                    int tierCmp = Integer.compare(tierRank(b.getCurrentRank()), tierRank(a.getCurrentRank()));
                    if (tierCmp != 0) return tierCmp;
                    int mmrCmp = Integer.compare(b.getTotalMmr(), a.getTotalMmr());
                    if (mmrCmp != 0) return mmrCmp;
                    return Integer.compare(b.getTotalWins(), a.getTotalWins());
                })
                .collect(Collectors.toList());
    }

    private int tierRank(String rank) {
        if (rank == null) return 0;
        return switch (rank) {
            case "Gold"   -> 2;
            case "Silver" -> 1;
            default       -> 0;
        };
    }

    // ── Friends ──────────────────────────────────────────────────────────────────

    /**
     * Send a friend request from the current user to {@code toUsername}.
     * @return null on success, or an error message on failure
     */
    public String sendFriendRequest(String toUsername) {
        if (currentUser == null) return "Not logged in.";
        if (currentUser.equals(toUsername)) return "You cannot add yourself.";
        PlayerProfile target = getPlayerProfile(toUsername);
        if (target == null) return "Player \"" + toUsername + "\" not found.";
        if (currentProfile.hasFriend(toUsername)) return "Already friends with " + toUsername + ".";
        if (target.hasIncomingRequestFrom(currentUser)) return "Friend request already sent.";
        target.addIncomingRequest(currentUser);
        userRepository.save(target);
        notificationService.notifyFriendRequest(toUsername, currentUser);
        return null;
    }

    /**
     * Accept an incoming friend request from {@code fromUsername}.
     * Adds each player to the other's friends list and persists both profiles.
     */
    public boolean acceptFriendRequest(String fromUsername) {
        if (currentUser == null || currentProfile == null) return false;
        if (!currentProfile.hasIncomingRequestFrom(fromUsername)) return false;
        PlayerProfile other = getPlayerProfile(fromUsername);
        currentProfile.removeIncomingRequest(fromUsername);
        currentProfile.addFriend(fromUsername);
        if (other != null) other.addFriend(currentUser);
        userRepository.save(currentProfile);
        if (other != null) userRepository.save(other);
        return true;
    }

    /**
     * Decline an incoming friend request from {@code fromUsername}.
     */
    public boolean declineFriendRequest(String fromUsername) {
        if (currentUser == null || currentProfile == null) return false;
        if (!currentProfile.hasIncomingRequestFrom(fromUsername)) return false;
        currentProfile.removeIncomingRequest(fromUsername);
        userRepository.save(currentProfile);
        return true;
    }

    /**
     * Remove an existing friend. Removes from both sides and persists.
     */
    public boolean removeFriend(String friendUsername) {
        if (currentUser == null || currentProfile == null) return false;
        if (!currentProfile.hasFriend(friendUsername)) return false;
        PlayerProfile other = getPlayerProfile(friendUsername);
        currentProfile.removeFriend(friendUsername);
        if (other != null) other.removeFriend(currentUser);
        userRepository.save(currentProfile);
        if (other != null) userRepository.save(other);
        return true;
    }

    /**
     * Returns full profiles for all of the current user's friends.
     */
    public List<PlayerProfile> getFriends() {
        if (currentProfile == null) return List.of();
        return currentProfile.getFriends().stream()
                .map(this::getPlayerProfile)
                .filter(p -> p != null)
                .collect(Collectors.toList());
    }

    /**
     * Returns the list of usernames who have sent the current user a friend request.
     */
    public List<String> getPendingFriendRequests() {
        if (currentProfile == null) return List.of();
        return currentProfile.getIncomingFriendRequests();
    }

    /**
     * Check whether the given username is a friend of the current user.
     */
    public boolean isFriend(String username) {
        return currentProfile != null && currentProfile.hasFriend(username);
    }

    // ── Player status ────────────────────────────────────────────────────────

    /**
     * Returns a human-readable status string for the given player.
     * Used by the friends list to show online/in-game/in-queue status.
     */
    public String getPlayerStatusText(String username) {
        // First check local context (same instance — detailed status)
        PlayerContext ctx = getPlayerContext(username);
        if (ctx != null) {
            return switch (ctx) {
                case IN_GAME         -> "In Game";
                case IN_QUEUE        -> "In Queue";
                case IN_LOBBY        -> "In Lobby";
                case VIEWING_RESULTS -> "In Game";
                case DASHBOARD       -> "Online";
            };
        }
        // Fall back to cross-instance presence heartbeat
        if (presenceService.isOnline(username)) return "Online";
        return "Offline";
    }

    /**
     * Reload the current user's profile from disk to pick up cross-instance
     * changes (e.g. friend requests sent from another running instance).
     * Returns the list of new incoming friend request senders since the last
     * in-memory state, or an empty list if nothing changed.
     */
    public List<String> reloadProfileFromDisk() {
        if (currentUser == null || currentProfile == null) return List.of();
        List<String> oldRequests = new ArrayList<>(currentProfile.getIncomingFriendRequests());
        List<String> oldFriends  = new ArrayList<>(currentProfile.getFriends());

        // Re-read from disk (bypasses in-memory cache)
        PlayerProfile diskProfile = userRepository.loadFresh(currentUser);
        if (diskProfile == null) return List.of();

        // Update in-memory profile and database
        currentProfile = diskProfile;
        database.storeData("profile:" + currentUser, diskProfile);

        // Find new incoming requests
        List<String> newRequests = new ArrayList<>(diskProfile.getIncomingFriendRequests());
        newRequests.removeAll(oldRequests);

        return newRequests;
    }

    // Getters for sub-services
    public UserRepository getUserRepository() { return userRepository; }
    public EventBus getEventBus() { return eventBus; }
    public RoomManager getRoomManager() { return roomManager; }
    public GameSessionManager getSessionManager() { return sessionManager; }
    public MatchMaking getMatchMaking() { return matchMaking; }
    public Leaderboard getLeaderboard() { return leaderboard; }
    public NotificationService getNotificationService() { return notificationService; }
    public PresenceService getPresenceService() { return presenceService; }
    public Database getDatabase() { return database; }
}
