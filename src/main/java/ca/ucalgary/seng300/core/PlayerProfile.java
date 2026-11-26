package ca.ucalgary.seng300.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all stored data for a registered player.
 * Persisted in the Database stub under "profile:<username>".
 */
public class PlayerProfile {

    private final String username;
    private final String firstName;
    private final String lastName;
    private final String passwordHash;

    // ---- Stats ----
    private String currentRank;

    // gameName -> list of opponent usernames faced
    private final Map<String, List<String>> matchesPlayed;

    // gameName -> number of wins
    private final Map<String, Integer> wins;

    // gameName -> MMR value
    private final Map<String, Integer> mmrByGame;

    // most-recent game names, capped at 10, index 0 = most recent
    private final List<String> recentGameHistory;

    // ---- Friends ----
    private final List<String> friends;
    private final List<String> incomingFriendRequests;

    private String avatarPath;
    private LocalDate accountCreated;

    public PlayerProfile(String username, String firstName, String lastName, String passwordHash) {
        this.username          = username;
        this.firstName         = firstName;
        this.lastName          = lastName;
        this.passwordHash      = passwordHash;
        this.currentRank       = "Bronze";
        this.mmrByGame         = new HashMap<>();
        this.matchesPlayed     = new HashMap<>();
        this.wins              = new HashMap<>();
        this.recentGameHistory        = new ArrayList<>();
        this.friends                  = new ArrayList<>();
        this.incomingFriendRequests   = new ArrayList<>();
        this.avatarPath               = "/images/gameDinoLogo.png";
        this.accountCreated    = LocalDate.now();
    }

    // ---- Stat update methods ----

    /**
     * Record a completed match.
     * @param gameName  e.g. "tictactoe"
     * @param opponent  the opponent's username
     */
    public void recordMatch(String gameName, String opponent) {
        matchesPlayed.computeIfAbsent(gameName, k -> new ArrayList<>()).add(opponent);
    }

    /**
     * Record a win for a given game.
     * @param gameName  e.g. "tictactoe"
     */
    public void recordWin(String gameName) {
        wins.merge(gameName, 1, Integer::sum);
    }

    /**
     * Add a game to the front of the recent history (max 4, no duplicates).
     * @param gameName  e.g. "tictactoe"
     */
    public void addRecentGame(String gameName) {
        recentGameHistory.remove(gameName);
        recentGameHistory.add(0, gameName);
        if (recentGameHistory.size() > 4) {
            recentGameHistory.remove(recentGameHistory.size() - 1);
        }
    }

    // ---- Getters ----

    public String getUsername()     { return username; }
    public String getFirstName()    { return firstName; }
    public String getLastName()     { return lastName; }
    public String getPasswordHash() { return passwordHash; }
    public String getCurrentRank()  { return currentRank; }

    public Map<String, List<String>> getMatchesPlayed()  { return matchesPlayed; }
    public Map<String, Integer> getWins()                { return wins; }
    public Map<String, Integer> getMmrByGame()           { return mmrByGame; }
    public List<String> getRecentGameHistory()           { return recentGameHistory; }

    // ---- Friends ----

    public void addFriend(String username) {
        if (!friends.contains(username)) friends.add(username);
    }

    public void removeFriend(String username) { friends.remove(username); }

    public boolean hasFriend(String username) { return friends.contains(username); }

    public List<String> getFriends() { return friends; }

    public void addIncomingRequest(String fromUsername) {
        if (!incomingFriendRequests.contains(fromUsername)) incomingFriendRequests.add(fromUsername);
    }

    public void removeIncomingRequest(String fromUsername) { incomingFriendRequests.remove(fromUsername); }

    public boolean hasIncomingRequestFrom(String fromUsername) { return incomingFriendRequests.contains(fromUsername); }

    public List<String> getIncomingFriendRequests() { return incomingFriendRequests; }

    public void setCurrentRank(String rank) { this.currentRank = rank; }

    public void setMmr(String gameType, int mmr) { mmrByGame.put(gameType, mmr); }

    public int getTotalMmr() {
        return mmrByGame.values().stream().mapToInt(Integer::intValue).sum();
    }

    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }

    public LocalDate getAccountCreated() { return accountCreated; }
    public void setAccountCreated(LocalDate accountCreated) { this.accountCreated = accountCreated; }

    /**
     * Total matches played across all games.
     */
    public int getTotalMatchesPlayed() {
        return matchesPlayed.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Total wins across all games.
     */
    public int getTotalWins() {
        return wins.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public String toString() {
        return "PlayerProfile{" +
                "username='" + username + '\'' +
                ", name='" + firstName + " " + lastName + '\'' +
                ", rank='" + currentRank + '\'' +
                ", totalMatches=" + getTotalMatchesPlayed() +
                ", totalWins=" + getTotalWins() +
                '}';
    }
}
